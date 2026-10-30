package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.SemanticDatasetRegistry;
import com.nexo.app.modules.assistente.application.SemanticResourceCountRegistry;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import com.nexo.app.modules.desconto.DescontoAnalyticsApi;
import com.nexo.app.modules.sst.SstAnalyticsApi;
import com.nexo.app.shared.application.analytics.SemanticQuery;
import com.nexo.app.shared.application.analytics.SemanticQueryResult;
import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Reuso das capacidades analiticas ja existentes no Nexo: datasets semanticos JPA publicados pelos modulos
 * (SST, descontos), contagens cadastrais e metricas oficiais calculadas pelos proprios modulos.
 */
@Configuration(proxyBeanMethods = false)
public class DatasetsOficiaisTools {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final List<String> METRICAS = List.of("media_mensal_inspecoes", "ranking_desvios_equipes",
            "atingimento_metas_inspecoes", "cobertura_colaboradores", "acidentalidade", "taxa_sucesso_descontos");

    @Bean
    AgentTool consultarDatasetTool(SemanticDatasetRegistry registry, ObjectMapper objectMapper, ResultStore resultStore,
            ResultFormatter formatter, AssistenteProperties properties) {
        return new BaseTool("consultar_dataset", "Consultar dataset oficial",
                "Consulta um dataset semantico oficial publicado pelos modulos (ex.: sst_inspecoes, sst_desvios, "
                        + "sst_eventos, descontos), com dimensoes, medidas, filtros, agrupamento e ordenacao definidos "
                        + "pelo descritor (veja descrever_fonte_dados). Operadores: EQ, NE, IN, CONTAINS, GT, GTE, LT, "
                        + "LTE, BETWEEN, IS_NULL, IS_NOT_NULL. Datas ISO-8601.",
                JsonSchemas.object()
                        .required("dataset", JsonSchemas.string("Id do dataset"))
                        .prop("dimensoes", JsonSchemas.arrayOfStrings(null))
                        .prop("medidas", JsonSchemas.arrayOfStrings(null))
                        .prop("filtros", JsonSchemas.array(null, JsonSchemas.object()
                                .required("campo", JsonSchemas.string(null))
                                .required("operador", JsonSchemas.string(null))
                                .prop("valores", JsonSchemas.arrayOfStrings(null)).build()))
                        .prop("agruparPor", JsonSchemas.arrayOfStrings("Mesmas dimensoes selecionadas, quando houver medidas"))
                        .prop("ordenarPor", JsonSchemas.array(null, JsonSchemas.object()
                                .required("campo", JsonSchemas.string(null))
                                .prop("direcao", JsonSchemas.enumString(null, List.of("ASC", "DESC"))).build()))
                        .prop("limite", JsonSchemas.integer(null, 1, 200))
                        .prop("titulo", JsonSchemas.string(null))
                        .build(),
                ToolKind.DATA, Set.of(), Duration.ofSeconds(45)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !registry.listarAutorizados(context.security()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Consultando dataset " + Objects.toString(ToolArgs.text(arguments, "dataset"), "oficial");
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                SemanticQuery query;
                try {
                    query = objectMapper.treeToValue(arguments, SemanticQuery.class);
                } catch (Exception exception) {
                    throw new ToolException("Consulta invalida para o dataset: " + exception.getMessage());
                }
                SemanticQueryResult result;
                try {
                    result = registry.executar(query, context.security());
                } catch (AccessDeniedException exception) {
                    throw new ToolException("Seu perfil nao tem acesso a este dataset.");
                } catch (IllegalArgumentException exception) {
                    throw new ToolException(exception.getMessage());
                }
                TabularData data = TabularData.fromMaps(result.linhas(), result.truncado());
                ResultStore.StoredResult stored = resultStore.registrar(context.run(), name(),
                        ResultadoAssistente.Natureza.INTERNO,
                        Optional.ofNullable(ToolArgs.text(arguments, "titulo")).orElse("Dataset " + result.dataset()),
                        data, result.consultaEfetiva());
                return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(),
                        Map.of("dataset", result.dataset(), "versao", result.datasetVersion(),
                                "consulta_efetiva", result.consultaEfetiva()), null);
            }
        };
    }

    @Bean
    AgentTool contarCadastrosTool(SemanticResourceCountRegistry registry, ResultStore resultStore,
            ResultFormatter formatter, AssistenteProperties properties) {
        return new BaseTool("contar_cadastros", "Contar cadastros",
                "Conta cadastros do Nexo (usuarios, colaboradores, equipes, materiais, estoques, contratos, chamados...). "
                        + "Use os ids retornados por listar_fontes_dados em 'cadastros_contaveis'. Ate 20 por chamada.",
                JsonSchemas.object().required("recursos", JsonSchemas.arrayOfStrings("Ids dos cadastros")).build(),
                ToolKind.DATA, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !registry.listarAutorizados(context.security()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Contando cadastros";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                List<String> recursos = new ArrayList<>(new LinkedHashSet<>(ToolArgs.texts(arguments, "recursos")));
                if (recursos.isEmpty()) throw new ToolException("Informe ao menos um recurso.");
                if (recursos.size() > 20) throw new ToolException("Maximo de 20 recursos por chamada.");
                Map<String, SemanticResourceCountProvider.ResourceCountDescriptor> autorizados = new LinkedHashMap<>();
                registry.listarAutorizados(context.security()).forEach(item -> autorizados.put(item.id(), item));
                List<List<Object>> linhas = new ArrayList<>();
                for (String recurso : recursos) {
                    var descritor = autorizados.get(recurso);
                    if (descritor == null) throw new ToolException("Cadastro '" + recurso + "' indisponivel ou desconhecido.");
                    linhas.add(List.of(recurso, descritor.nome(), registry.contar(recurso, context.security())));
                }
                TabularData data = new TabularData(List.of(
                        new TabularData.Column("recurso", TabularData.ColumnType.TEXTO),
                        new TabularData.Column("nome", TabularData.ColumnType.TEXTO),
                        new TabularData.Column("total", TabularData.ColumnType.INTEIRO)), linhas, linhas.size(), false);
                ResultStore.StoredResult stored = resultStore.registrar(context.run(), name(),
                        ResultadoAssistente.Natureza.INTERNO, "Contagem de cadastros", data, Map.of("recursos", recursos));
                return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(), null, null);
            }
        };
    }

    @Bean
    AgentTool metricaOficialTool(SstAnalyticsApi sstApi, DescontoAnalyticsApi descontoApi, ObjectMapper objectMapper,
            ResultFormatter formatter, AssistenteProperties properties) {
        return new BaseTool("metrica_oficial", "Calcular métrica oficial",
                "Calcula metricas OFICIAIS dos modulos (prefira-as a recalcular o mesmo numero): "
                        + "media_mensal_inspecoes (mes_referencia, quantidade_meses), ranking_desvios_equipes (inicio, fim, "
                        + "limite), atingimento_metas_inspecoes (inicio, fim, limite: meta x realizado por inspetor), "
                        + "cobertura_colaboradores (inicio, fim), acidentalidade (inicio, fim), taxa_sucesso_descontos "
                        + "(inicio, fim, amostra_minima, limite). Datas AAAA-MM-DD; fim exclusivo quando aplicavel.",
                JsonSchemas.object()
                        .required("metrica", JsonSchemas.enumString(null, METRICAS))
                        .prop("inicio", JsonSchemas.string(null))
                        .prop("fim", JsonSchemas.string(null))
                        .prop("mes_referencia", JsonSchemas.string(null))
                        .prop("quantidade_meses", JsonSchemas.integer(null, 1, 36))
                        .prop("limite", JsonSchemas.integer(null, 1, 100))
                        .prop("amostra_minima", JsonSchemas.integer(null, 1, 1000))
                        .build(),
                ToolKind.DATA, Set.of("sst:dashboard:read", "desconto:read"), Duration.ofSeconds(60)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Calculando métrica oficial";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String metrica = ToolArgs.requiredText(arguments, "metrica");
                Integer limite = ToolArgs.integer(arguments, "limite");
                Object resultado = switch (metrica) {
                    case "media_mensal_inspecoes" -> {
                        exigir(context, "sst:dashboard:read");
                        String referencia = ToolArgs.text(arguments, "mes_referencia");
                        Integer meses = ToolArgs.integer(arguments, "quantidade_meses");
                        yield sstApi.calcularMediaMensalInspecoes(referencia == null ? null : ToolArgs.parseDate(referencia, "mes_referencia"),
                                meses == null ? 12 : meses);
                    }
                    case "ranking_desvios_equipes" -> {
                        exigir(context, "sst:dashboard:read");
                        OffsetDateTime fim = inicioDoDia(arguments, "fim", LocalDate.now(ZONA).plusDays(1));
                        OffsetDateTime inicio = inicioDoDia(arguments, "inicio", fim.toLocalDate().minusMonths(3));
                        yield sstApi.calcularRankingEquipesDesvios(inicio, fim, limite == null ? 10 : limite);
                    }
                    case "atingimento_metas_inspecoes" -> {
                        exigir(context, "sst:dashboard:read");
                        String inicio = ToolArgs.text(arguments, "inicio");
                        String fim = ToolArgs.text(arguments, "fim");
                        yield sstApi.calcularAtingimentoMetasInspecoes(
                                inicio == null ? null : ToolArgs.parseDate(inicio, "inicio"),
                                fim == null ? null : ToolArgs.parseDate(fim, "fim"), limite == null ? 20 : limite);
                    }
                    case "cobertura_colaboradores" -> {
                        exigir(context, "sst:dashboard:read");
                        OffsetDateTime fim = inicioDoDia(arguments, "fim", LocalDate.now(ZONA).plusDays(1));
                        OffsetDateTime inicio = inicioDoDia(arguments, "inicio", fim.toLocalDate().minusMonths(3));
                        yield sstApi.calcularCoberturaColaboradores(inicio, fim);
                    }
                    case "acidentalidade" -> {
                        exigir(context, "sst:dashboard:read");
                        OffsetDateTime fim = inicioDoDia(arguments, "fim", LocalDate.now(ZONA).plusDays(1));
                        OffsetDateTime inicio = inicioDoDia(arguments, "inicio", fim.toLocalDate().minusMonths(12));
                        yield sstApi.calcularAcidentalidade(inicio, fim);
                    }
                    case "taxa_sucesso_descontos" -> {
                        exigir(context, "desconto:read");
                        OffsetDateTime fim = inicioDoDia(arguments, "fim", LocalDate.now(ZONA).plusDays(1));
                        OffsetDateTime inicio = inicioDoDia(arguments, "inicio", fim.toLocalDate().minusMonths(12));
                        Integer amostra = ToolArgs.integer(arguments, "amostra_minima");
                        yield descontoApi.rankingTaxaSucessoPorTipoEquipamento(inicio, fim,
                                amostra == null ? Math.max(1, properties.getDiscountMinSample()) : amostra,
                                limite == null ? 10 : limite);
                    }
                    default -> throw new ToolException("Metrica desconhecida. Use: " + String.join(", ", METRICAS));
                };
                String json = formatter.json(resultado);
                int max = properties.getAgent().getToolResultMaxChars();
                if (json.length() > max) json = json.substring(0, max) + "...[truncado]";
                return ToolResult.of(json, "Metrica oficial calculada");
            }
        };
    }

    private static void exigir(ToolContext context, String autoridade) {
        if (!context.security().possuiAutoridade(autoridade)) {
            throw new ToolException("Seu perfil nao tem acesso a esta metrica.");
        }
    }

    private static OffsetDateTime inicioDoDia(JsonNode arguments, String campo, LocalDate padrao) {
        String valor = ToolArgs.text(arguments, campo);
        if (valor == null) return padrao.atStartOfDay(ZONA).toOffsetDateTime();
        try {
            return OffsetDateTime.parse(valor);
        } catch (RuntimeException ignored) {
            return ToolArgs.parseDate(valor, campo).atStartOfDay(ZONA).toOffsetDateTime();
        }
    }
}
