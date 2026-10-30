package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.semantic.ConsultaDadosService;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.modules.assistente.application.semantic.ViewQuery;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Consultas genericas da camada semantica: consulta estruturada a qualquer fonte, SQL analitico validado e
 * comparacao de periodos.
 */
@Configuration(proxyBeanMethods = false)
public class ConsultaTools {

    private static final List<String> FUNCOES = List.of("contagem", "contagem_distinta", "soma", "media", "minimo",
            "maximo", "mediana");
    private static final List<String> OPERADORES = List.of("igual", "diferente", "em", "nao_em", "contem", "maior",
            "maior_igual", "menor", "menor_igual", "entre", "nulo", "nao_nulo");

    @Bean
    AgentTool consultarFonteTool(ConsultaDadosService consultas, SemanticCatalog catalog) {
        ObjectNode metrica = JsonSchemas.object()
                .required("funcao", JsonSchemas.enumString(null, FUNCOES))
                .prop("coluna", JsonSchemas.string("Coluna (omitir em contagem de linhas)"))
                .prop("alias", JsonSchemas.string("Nome da metrica no resultado (snake_case)"))
                .build();
        ObjectNode filtro = JsonSchemas.object()
                .required("coluna", JsonSchemas.string(null))
                .required("operador", JsonSchemas.enumString(null, OPERADORES))
                .prop("valores", JsonSchemas.array("Valores (1 para comparacoes, 2 para entre, varios para em)",
                        JsonSchemas.anyValue(null)))
                .build();
        ObjectNode ordem = JsonSchemas.object()
                .required("campo", JsonSchemas.string("Coluna selecionada ou alias de metrica"))
                .prop("decrescente", JsonSchemas.bool(null)).build();
        return new BaseTool("consultar_fonte", "Consultar dados do Nexo",
                "Consulta estruturada (sem SQL) a QUALQUER fonte da camada semantica do Nexo — inclusive descontos, "
                        + "financeiro, SST, IAM, sincronizacao, contratos, execucao. Informe a fonte, colunas "
                        + "(lista detalhada) ou agrupar_por + metricas (agregacao), filtros e periodo. Use descrever_fonte_dados "
                        + "para conhecer as colunas. Prefira as ferramentas consultar_* especificas quando existirem.",
                JsonSchemas.object()
                        .required("fonte", JsonSchemas.string("Id da fonte, ex.: descontos, solicitacoes_compra, inspecoes"))
                        .prop("colunas", JsonSchemas.arrayOfStrings("Colunas para listagem detalhada (sem agregacao)"))
                        .prop("agrupar_por", JsonSchemas.arrayOfStrings("Colunas de agrupamento"))
                        .prop("metricas", JsonSchemas.array("Metricas agregadas", metrica))
                        .prop("filtros", JsonSchemas.array("Filtros combinados com E", filtro))
                        .prop("periodo", JsonSchemas.object()
                                .prop("coluna", JsonSchemas.string("Coluna de data (padrao: data principal da fonte)"))
                                .prop("inicio", JsonSchemas.string("AAAA-MM-DD"))
                                .prop("fim", JsonSchemas.string("AAAA-MM-DD")).build())
                        .prop("ordenar_por", JsonSchemas.array(null, ordem))
                        .prop("limite", JsonSchemas.integer("Maximo de linhas (padrao 200)", 1, 5000))
                        .prop("titulo", JsonSchemas.string("Titulo curto do resultado"))
                        .build(),
                ToolKind.DATA, Set.of(), Duration.ofSeconds(45)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return consultas.disponivel();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                String fonte = ToolArgs.text(arguments, "fonte");
                return catalog.fonte(fonte).map(item -> "Consultando " + item.nome().toLowerCase(Locale.ROOT))
                        .orElse("Consultando dados do Nexo");
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                ViewQuery consulta = lerConsulta(arguments);
                return consultas.consultarComoFerramenta(consulta, ToolArgs.text(arguments, "titulo"), name(), context);
            }
        };
    }

    @Bean
    AgentTool consultarSqlTool(ConsultaDadosService consultas, SemanticCatalog catalog) {
        return new BaseTool("consultar_sql", "Consulta SQL analítica",
                "Executa SQL analitico SOMENTE LEITURA (PostgreSQL) sobre as views do schema copiloto — use para "
                        + "analises que exigem juncoes entre fontes, CTEs, funcoes de janela, percentuais ou comparacoes "
                        + "complexas. Um unico SELECT/WITH; sem escrita, sem schemas do sistema, sem funcoes de sistema. "
                        + "Referencie as views como copiloto.<fonte> (ex.: copiloto.movimentacoes). Descubra colunas "
                        + "com descrever_fonte_dados. Datas estao em America/Sao_Paulo; timestamps usam timestamptz. "
                        + "O resultado fica salvo como rN para graficos e relatorios.",
                JsonSchemas.object()
                        .required("sql", JsonSchemas.string("Consulta SELECT/WITH em PostgreSQL"))
                        .required("titulo", JsonSchemas.string("Titulo curto do que a consulta responde"))
                        .build(),
                ToolKind.SQL, Set.of(), Duration.ofSeconds(40)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return consultas.disponivel() && catalog.schemaDisponivel();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                String titulo = ToolArgs.text(arguments, "titulo");
                return titulo == null ? "Executando consulta analítica" : "Consultando: " + titulo;
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                return consultas.sql(ToolArgs.requiredText(arguments, "sql"), ToolArgs.text(arguments, "titulo"), context);
            }
        };
    }

    @Bean
    AgentTool compararPeriodosTool(ConsultaDadosService consultas, SemanticCatalog catalog, ResultStore resultStore,
            ResultFormatter formatter, AssistenteProperties properties) {
        ObjectNode periodo = JsonSchemas.object()
                .required("inicio", JsonSchemas.string("AAAA-MM-DD"))
                .required("fim", JsonSchemas.string("AAAA-MM-DD"))
                .prop("rotulo", JsonSchemas.string("Rotulo do periodo, ex.: agosto/2026"))
                .build();
        ObjectNode filtro = JsonSchemas.object()
                .required("coluna", JsonSchemas.string(null))
                .required("operador", JsonSchemas.enumString(null, OPERADORES))
                .prop("valores", JsonSchemas.array(null, JsonSchemas.anyValue(null))).build();
        return new BaseTool("comparar_periodos", "Comparar períodos",
                "Compara uma metrica entre dois periodos (ex.: agosto x setembro) em qualquer fonte, opcionalmente por "
                        + "uma dimensao (material, base, status...). Retorna valor de cada periodo, variacao absoluta e "
                        + "percentual por linha, ja registrado como resultado. A variacao e sempre periodo mais recente "
                        + "menos periodo anterior (positivo = aumento), qualquer que seja a ordem informada.",
                JsonSchemas.object()
                        .required("fonte", JsonSchemas.string("Id da fonte, ex.: movimentacoes, descontos, solicitacoes_compra"))
                        .required("funcao", JsonSchemas.enumString("Funcao da metrica", FUNCOES))
                        .prop("coluna", JsonSchemas.string("Coluna da metrica (omitir para contagem)"))
                        .prop("dimensao", JsonSchemas.string("Coluna para detalhar a comparacao (opcional)"))
                        .prop("coluna_data", JsonSchemas.string("Coluna de data (padrao: data principal da fonte)"))
                        .required("periodo_a", periodo)
                        .required("periodo_b", periodo)
                        .prop("filtros", JsonSchemas.array("Filtros aplicados aos dois periodos", filtro))
                        .prop("limite", JsonSchemas.integer("Maximo de linhas por periodo", 1, 1000))
                        .prop("titulo", JsonSchemas.string(null))
                        .build(),
                ToolKind.ANALYSIS, Set.of(), Duration.ofSeconds(60)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return consultas.disponivel();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Comparando períodos";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String fonte = ToolArgs.requiredText(arguments, "fonte");
                String funcao = ToolArgs.requiredText(arguments, "funcao");
                String coluna = ToolArgs.text(arguments, "coluna");
                String dimensao = ToolArgs.text(arguments, "dimensao");
                String colunaData = ToolArgs.text(arguments, "coluna_data");
                JsonNode primeiro = ToolArgs.object(arguments, "periodo_a");
                JsonNode segundo = ToolArgs.object(arguments, "periodo_b");
                if (primeiro == null || segundo == null) throw new ToolException("Informe periodo_a e periodo_b.");
                // A variacao e sempre "periodo mais recente - periodo mais antigo", independente da ordem informada.
                LocalDate inicioPrimeiro = ToolArgs.date(primeiro.get("inicio"), "periodo_a.inicio");
                LocalDate inicioSegundo = ToolArgs.date(segundo.get("inicio"), "periodo_b.inicio");
                boolean inverter = inicioPrimeiro != null && inicioSegundo != null && inicioPrimeiro.isAfter(inicioSegundo);
                JsonNode a = inverter ? segundo : primeiro;
                JsonNode b = inverter ? primeiro : segundo;
                List<ViewQuery.Filtro> filtros = lerFiltros(ToolArgs.array(arguments, "filtros"));
                Integer limite = ToolArgs.integer(arguments, "limite");
                List<String> grupo = dimensao == null ? List.of() : List.of(dimensao);
                ViewQuery.Metrica metrica = new ViewQuery.Metrica(funcao, coluna, "valor");
                String rotuloA = Optional.ofNullable(ToolArgs.text(a, "rotulo")).orElse("periodo_a");
                String rotuloB = Optional.ofNullable(ToolArgs.text(b, "rotulo")).orElse("periodo_b");
                ViewQuery consultaA = new ViewQuery(fonte, List.of(), grupo, List.of(metrica), filtros,
                        new ViewQuery.Periodo(colunaData, ToolArgs.date(a.get("inicio"), "periodo_a.inicio"),
                                ToolArgs.date(a.get("fim"), "periodo_a.fim")), List.of(), limite == null ? 500 : limite);
                ViewQuery consultaB = new ViewQuery(fonte, List.of(), grupo, List.of(metrica), filtros,
                        new ViewQuery.Periodo(colunaData, ToolArgs.date(b.get("inicio"), "periodo_b.inicio"),
                                ToolArgs.date(b.get("fim"), "periodo_b.fim")), List.of(), limite == null ? 500 : limite);
                TabularData dadosA = consultas.executarSemRegistro(consultaA, context);
                TabularData dadosB = consultas.executarSemRegistro(consultaB, context);

                Map<String, double[]> porChave = new LinkedHashMap<>();
                acumular(porChave, dadosA, dimensao, 0);
                acumular(porChave, dadosB, dimensao, 1);
                String nomeDimensao = dimensao == null ? "indicador" : dimensao.toLowerCase(Locale.ROOT);
                List<TabularData.Column> colunas = List.of(
                        new TabularData.Column(nomeDimensao, TabularData.ColumnType.TEXTO),
                        new TabularData.Column(chave(rotuloA), TabularData.ColumnType.NUMERO),
                        new TabularData.Column(chave(rotuloB), TabularData.ColumnType.NUMERO),
                        new TabularData.Column("variacao", TabularData.ColumnType.NUMERO),
                        new TabularData.Column("variacao_percentual", TabularData.ColumnType.NUMERO));
                List<List<Object>> linhas = new ArrayList<>();
                porChave.forEach((key, valores) -> {
                    double diferenca = valores[1] - valores[0];
                    Double percentual = valores[0] == 0 ? null : Math.round((diferenca / valores[0]) * 1000.0) / 10.0;
                    List<Object> linha = new ArrayList<>();
                    linha.add(key);
                    linha.add(arredondar(valores[0]));
                    linha.add(arredondar(valores[1]));
                    linha.add(arredondar(diferenca));
                    linha.add(percentual);
                    linhas.add(linha);
                });
                linhas.sort((x, y) -> Double.compare(Math.abs(((Number) y.get(3)).doubleValue()),
                        Math.abs(((Number) x.get(3)).doubleValue())));
                String titulo = Optional.ofNullable(ToolArgs.text(arguments, "titulo"))
                        .orElse("Comparação " + rotuloA + " x " + rotuloB);
                ResultStore.StoredResult stored = resultStore.registrar(context.run(), name(),
                        ResultadoAssistente.Natureza.CALCULO, titulo,
                        new TabularData(colunas, linhas, linhas.size(), false),
                        Map.of("fonte", fonte, "periodo_a", a.toString(), "periodo_b", b.toString()));
                double totalA = porChave.values().stream().mapToDouble(v -> v[0]).sum();
                double totalB = porChave.values().stream().mapToDouble(v -> v[1]).sum();
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("total_" + chave(rotuloA), arredondar(totalA));
                extra.put("total_" + chave(rotuloB), arredondar(totalB));
                extra.put("variacao_total", arredondar(totalB - totalA));
                extra.put("variacao_total_percentual", totalA == 0 ? null : Math.round(((totalB - totalA) / totalA) * 1000.0) / 10.0);
                extra.put("leitura", "variacao = " + rotuloB + " (mais recente) - " + rotuloA + " (anterior); percentual "
                        + "relativo a " + rotuloA + ". Valor positivo = aumento em " + rotuloB + ".");
                return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(), extra,
                        rotuloA + " x " + rotuloB);
            }
        };
    }

    static ViewQuery lerConsulta(JsonNode arguments) {
        String fonte = ToolArgs.requiredText(arguments, "fonte");
        List<ViewQuery.Metrica> metricas = new ArrayList<>();
        JsonNode metricasNode = ToolArgs.array(arguments, "metricas");
        if (metricasNode != null) {
            metricasNode.forEach(item -> metricas.add(new ViewQuery.Metrica(ToolArgs.text(item, "funcao"),
                    ToolArgs.text(item, "coluna"), ToolArgs.text(item, "alias"))));
        }
        ViewQuery.Periodo periodo = null;
        JsonNode periodoNode = ToolArgs.object(arguments, "periodo");
        if (periodoNode != null) {
            LocalDate inicio = ToolArgs.date(periodoNode.get("inicio"), "periodo.inicio");
            LocalDate fim = ToolArgs.date(periodoNode.get("fim"), "periodo.fim");
            if (inicio != null || fim != null) periodo = new ViewQuery.Periodo(ToolArgs.text(periodoNode, "coluna"), inicio, fim);
        }
        List<ViewQuery.Ordem> ordens = new ArrayList<>();
        JsonNode ordensNode = ToolArgs.array(arguments, "ordenar_por");
        if (ordensNode != null) {
            ordensNode.forEach(item -> ordens.add(new ViewQuery.Ordem(ToolArgs.text(item, "campo"),
                    ToolArgs.bool(item, "decrescente", false))));
        }
        return new ViewQuery(fonte, ToolArgs.texts(arguments, "colunas"), ToolArgs.texts(arguments, "agrupar_por"),
                metricas, lerFiltros(ToolArgs.array(arguments, "filtros")), periodo, ordens,
                ToolArgs.integer(arguments, "limite"));
    }

    static List<ViewQuery.Filtro> lerFiltros(JsonNode filtrosNode) {
        List<ViewQuery.Filtro> filtros = new ArrayList<>();
        if (filtrosNode == null) return filtros;
        for (JsonNode item : filtrosNode) {
            List<Object> valores = new ArrayList<>();
            JsonNode valoresNode = item.get("valores");
            if (valoresNode == null) valoresNode = item.get("valor");
            if (valoresNode != null && !valoresNode.isNull()) {
                if (valoresNode.isArray()) valoresNode.forEach(valor -> valores.add(valor.isNumber() ? valor.numberValue() : valor.asText()));
                else valores.add(valoresNode.isNumber() ? valoresNode.numberValue() : valoresNode.asText());
            }
            filtros.add(new ViewQuery.Filtro(ToolArgs.text(item, "coluna"), ToolArgs.text(item, "operador"), valores));
        }
        return filtros;
    }

    private static void acumular(Map<String, double[]> destino, TabularData dados, String dimensao, int posicao) {
        int indiceValor = dados.columnIndex("valor");
        int indiceDimensao = dimensao == null ? -1 : dados.columnIndex(dimensao);
        for (List<Object> linha : dados.rows()) {
            String chave = indiceDimensao < 0 ? "total" : String.valueOf(linha.get(indiceDimensao));
            Object valor = indiceValor < 0 ? null : linha.get(indiceValor);
            double numero = valor instanceof Number number ? number.doubleValue() : 0;
            destino.computeIfAbsent(chave, ignored -> new double[2])[posicao] += numero;
        }
    }

    private static Object arredondar(double valor) {
        if (valor == Math.rint(valor) && Math.abs(valor) < 1e15) return (long) valor;
        return Math.round(valor * 10_000.0) / 10_000.0;
    }

    private static String chave(String rotulo) {
        String normalizado = SemanticCatalog.normalizar(rotulo).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return normalizado.isBlank() ? "periodo" : normalizado;
    }
}
