package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.semantic.ConsultaDadosService;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.modules.assistente.application.semantic.ViewQuery;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Ferramenta de dominio declarativa (consultar_estoque, consultar_movimentacoes...). O schema e gerado a
 * partir do preset com enums de dimensoes, metricas e filtros; a execucao compila para SQL parametrizado
 * sobre a view da camada semantica. O modelo nunca escreve SQL por aqui.
 */
public class DomainQueryTool implements AgentTool {

    public enum TipoCampo { TEXTO, EXATO, ENUM, BOOLEANO, NUMERO }

    public record Campo(String nome, String coluna, TipoCampo tipo, List<String> valores, String descricao) {
        public static Campo texto(String nome, String descricao) {
            return new Campo(nome, nome, TipoCampo.TEXTO, List.of(), descricao);
        }

        public static Campo exato(String nome, String descricao) {
            return new Campo(nome, nome, TipoCampo.EXATO, List.of(), descricao);
        }

        public static Campo enumerado(String nome, String descricao, String... valores) {
            return new Campo(nome, nome, TipoCampo.ENUM, List.of(valores), descricao);
        }

        public static Campo booleano(String nome, String descricao) {
            return new Campo(nome, nome, TipoCampo.BOOLEANO, List.of(), descricao);
        }

        public static Campo numero(String nome, String descricao) {
            return new Campo(nome, nome, TipoCampo.NUMERO, List.of(), descricao);
        }
    }

    public record MetricaPreset(String id, String funcao, String coluna, String descricao) {
    }

    public record Preset(
            String nome,
            String rotulo,
            String progresso,
            String descricao,
            String fonte,
            boolean possuiPeriodo,
            List<Campo> filtros,
            List<String> dimensoes,
            List<MetricaPreset> metricas,
            List<String> colunasDetalhe,
            String ordemPadraoDetalhe) {
    }

    private final Preset preset;
    private final ConsultaDadosService consultas;
    private final Set<String> autoridades;
    private final JsonNode schema;

    public DomainQueryTool(Preset preset, ConsultaDadosService consultas, SemanticCatalog catalog) {
        this.preset = preset;
        this.consultas = consultas;
        SemanticCatalog.Fonte fonte = catalog.fonte(preset.fonte())
                .orElseThrow(() -> new IllegalStateException("Preset aponta para fonte inexistente: " + preset.fonte()));
        Set<String> exigidas = new LinkedHashSet<>(fonte.autoridades());
        exigidas.remove(SemanticCatalog.AUTENTICADO);
        this.autoridades = Set.copyOf(exigidas);
        this.schema = montarSchema(fonte);
    }

    @Override
    public String name() {
        return preset.nome();
    }

    @Override
    public String displayName() {
        return preset.rotulo();
    }

    @Override
    public String description() {
        return preset.descricao();
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolKind kind() {
        return ToolKind.DATA;
    }

    @Override
    public Set<String> requiredAuthorities() {
        return autoridades;
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(45);
    }

    @Override
    public boolean isAvailable(AgentRunContext context) {
        return consultas.disponivel();
    }

    @Override
    public String progressLabel(JsonNode arguments) {
        return preset.progresso();
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        List<ViewQuery.Filtro> filtros = new ArrayList<>();
        JsonNode informados = ToolArgs.object(arguments, "filtros");
        if (informados != null) {
            Map<String, Campo> porNome = new HashMap<>();
            preset.filtros().forEach(campo -> porNome.put(campo.nome(), campo));
            informados.fields().forEachRemaining(entry -> {
                Campo campo = porNome.get(entry.getKey());
                if (campo == null) {
                    throw new ToolException("Filtro '" + entry.getKey() + "' nao existe. Filtros validos: "
                            + String.join(", ", porNome.keySet()));
                }
                filtros.addAll(filtro(campo, entry.getValue()));
            });
        }
        ViewQuery.Periodo periodo = null;
        JsonNode periodoNode = ToolArgs.object(arguments, "periodo");
        if (periodoNode != null && preset.possuiPeriodo()) {
            LocalDate inicio = ToolArgs.date(periodoNode.get("inicio"), "periodo.inicio");
            LocalDate fim = ToolArgs.date(periodoNode.get("fim"), "periodo.fim");
            if (inicio != null || fim != null) periodo = new ViewQuery.Periodo(null, inicio, fim);
        }
        boolean detalhado = ToolArgs.bool(arguments, "detalhado", false);
        List<String> agrupar = ToolArgs.texts(arguments, "agrupar_por");
        for (String dimensao : agrupar) {
            if (!preset.dimensoes().contains(dimensao)) {
                throw new ToolException("Nao e possivel agrupar por '" + dimensao + "'. Opcoes: "
                        + String.join(", ", preset.dimensoes()));
            }
        }
        List<String> metricasPedidas = ToolArgs.texts(arguments, "metricas");
        Map<String, MetricaPreset> metricasPorId = new LinkedHashMap<>();
        preset.metricas().forEach(metrica -> metricasPorId.put(metrica.id(), metrica));
        List<ViewQuery.Metrica> metricas = new ArrayList<>();
        for (String id : metricasPedidas) {
            MetricaPreset metrica = metricasPorId.get(id);
            if (metrica == null) {
                throw new ToolException("Metrica '" + id + "' nao existe. Opcoes: " + String.join(", ", metricasPorId.keySet()));
            }
            metricas.add(new ViewQuery.Metrica(metrica.funcao(), metrica.coluna(), metrica.id()));
        }
        List<ViewQuery.Ordem> ordem = ordem(ToolArgs.text(arguments, "ordenar_por"));
        ViewQuery consulta;
        if (detalhado) {
            if (ordem.isEmpty() && preset.ordemPadraoDetalhe() != null) ordem = ordem(preset.ordemPadraoDetalhe());
            consulta = new ViewQuery(preset.fonte(), preset.colunasDetalhe(), List.of(), List.of(), filtros, periodo,
                    ordem, ToolArgs.integer(arguments, "limite"));
        } else {
            if (metricas.isEmpty()) {
                preset.metricas().forEach(metrica -> metricas.add(
                        new ViewQuery.Metrica(metrica.funcao(), metrica.coluna(), metrica.id())));
            }
            consulta = new ViewQuery(preset.fonte(), List.of(), agrupar, metricas, filtros, periodo, ordem,
                    ToolArgs.integer(arguments, "limite"));
        }
        String titulo = ToolArgs.text(arguments, "titulo");
        return consultas.consultarComoFerramenta(consulta, titulo == null ? preset.rotulo() : titulo, preset.nome(), context);
    }

    private List<ViewQuery.Filtro> filtro(Campo campo, JsonNode valor) {
        if (valor == null || valor.isNull()) return List.of();
        return switch (campo.tipo()) {
            case TEXTO -> List.of(new ViewQuery.Filtro(campo.coluna(), "contem", valores(valor)));
            case EXATO -> {
                List<Object> valores = valores(valor);
                yield List.of(new ViewQuery.Filtro(campo.coluna(), valores.size() > 1 ? "em" : "igual", valores));
            }
            case ENUM -> {
                List<Object> valores = new ArrayList<>();
                for (Object item : valores(valor)) {
                    String texto = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
                    if (!campo.valores().contains(texto)) {
                        throw new ToolException("Valor '" + item + "' invalido para " + campo.nome() + ". Validos: "
                                + String.join(", ", campo.valores()));
                    }
                    valores.add(texto);
                }
                yield List.of(new ViewQuery.Filtro(campo.coluna(), "em", valores));
            }
            case BOOLEANO -> List.of(new ViewQuery.Filtro(campo.coluna(), "igual",
                    List.of(valor.isBoolean() ? valor.asBoolean() : Boolean.parseBoolean(valor.asText()))));
            case NUMERO -> {
                List<ViewQuery.Filtro> filtros = new ArrayList<>();
                if (valor.isNumber()) {
                    filtros.add(new ViewQuery.Filtro(campo.coluna(), "igual", List.of(valor.asText())));
                } else {
                    if (valor.hasNonNull("min")) filtros.add(new ViewQuery.Filtro(campo.coluna(), "maior_igual",
                            List.of(valor.get("min").asText())));
                    if (valor.hasNonNull("max")) filtros.add(new ViewQuery.Filtro(campo.coluna(), "menor_igual",
                            List.of(valor.get("max").asText())));
                }
                yield filtros;
            }
        };
    }

    private static List<Object> valores(JsonNode valor) {
        List<Object> valores = new ArrayList<>();
        if (valor.isArray()) {
            valor.forEach(item -> {
                if (item != null && !item.isNull() && !item.asText().isBlank()) valores.add(item.asText().trim());
            });
        } else if (!valor.asText().isBlank()) {
            valores.add(valor.asText().trim());
        }
        if (valores.isEmpty()) throw new ToolException("Filtro sem valor.");
        return valores;
    }

    private static List<ViewQuery.Ordem> ordem(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        List<ViewQuery.Ordem> ordens = new ArrayList<>();
        for (String parte : texto.split(",")) {
            String campo = parte.trim();
            if (campo.isEmpty()) continue;
            boolean desc = campo.startsWith("-") || campo.toLowerCase(Locale.ROOT).endsWith(" desc");
            campo = campo.replaceFirst("^-", "").replaceAll("(?i)\\s+(asc|desc)$", "").trim();
            ordens.add(new ViewQuery.Ordem(campo, desc));
        }
        return ordens;
    }

    private JsonNode montarSchema(SemanticCatalog.Fonte fonte) {
        JsonSchemas.ObjectBuilder filtros = JsonSchemas.object()
                .description("Filtros opcionais. Texto = contem (sem diferenciar maiusculas); listas = qualquer um dos valores.");
        for (Campo campo : preset.filtros()) {
            ObjectNode propriedade = switch (campo.tipo()) {
                case TEXTO, EXATO -> JsonSchemas.string(campo.descricao());
                case ENUM -> JsonSchemas.arrayOfEnum(campo.descricao(), campo.valores());
                case BOOLEANO -> JsonSchemas.bool(campo.descricao());
                case NUMERO -> JsonSchemas.object().description(campo.descricao())
                        .prop("min", JsonSchemas.number("Minimo inclusivo"))
                        .prop("max", JsonSchemas.number("Maximo inclusivo")).build();
            };
            filtros.prop(campo.nome(), propriedade);
        }
        JsonSchemas.ObjectBuilder builder = JsonSchemas.object()
                .prop("filtros", filtros.build())
                .prop("agrupar_por", JsonSchemas.arrayOfEnum("Dimensoes de agrupamento (ranking, distribuicao). "
                        + "Sem agrupamento e sem 'detalhado' retorna os totais gerais.", preset.dimensoes()))
                .prop("metricas", JsonSchemas.arrayOfEnum("Metricas calculadas (padrao: todas). "
                        + metricasDescritas(), preset.metricas().stream().map(MetricaPreset::id).toList()))
                .prop("detalhado", JsonSchemas.bool("true = lista registros individuais em vez de agregar"))
                .prop("ordenar_por", JsonSchemas.string("Coluna ou metrica; prefixe com '-' para decrescente (ex.: -quantidade_total)"))
                .prop("limite", JsonSchemas.integer("Maximo de linhas (padrao 200)", 1, 5000))
                .prop("titulo", JsonSchemas.string("Titulo curto do resultado, usado em graficos e relatorios"));
        if (preset.possuiPeriodo()) {
            builder.prop("periodo", JsonSchemas.object()
                    .description("Periodo por " + fonte.colunaData() + " (datas AAAA-MM-DD, inclusivas)")
                    .prop("inicio", JsonSchemas.string("Data inicial AAAA-MM-DD"))
                    .prop("fim", JsonSchemas.string("Data final AAAA-MM-DD")).build());
        }
        return builder.build();
    }

    private String metricasDescritas() {
        StringBuilder texto = new StringBuilder();
        for (MetricaPreset metrica : preset.metricas()) {
            if (!texto.isEmpty()) texto.append("; ");
            texto.append(metrica.id()).append(" = ").append(metrica.descricao());
        }
        return texto.toString();
    }
}
