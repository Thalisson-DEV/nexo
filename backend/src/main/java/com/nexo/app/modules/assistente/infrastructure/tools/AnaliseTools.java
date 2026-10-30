package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.results.TabularOps;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.*;

/**
 * Analises deterministicas sobre resultados ja registrados (rN): agregacoes, series temporais,
 * estatisticas, conciliacao entre fontes (ex.: planilha x Nexo) e deteccao de anomalias.
 */
@Configuration(proxyBeanMethods = false)
public class AnaliseTools {

    @Bean
    AgentTool analisarResultadoTool(ResultStore store, ResultFormatter formatter, AssistenteProperties properties) {
        return new BaseTool("analisar_resultado", "Analisar resultado",
                "Analisa um resultado ja registrado na conversa (r1, r2...) sem nova consulta: 'estatisticas' "
                        + "(resumo de cada coluna), 'agrupar' (dimensoes + metrica + agregacao), 'serie_temporal' "
                        + "(coluna_data + granularidade dia/semana/mes/ano) e 'filtrar' (coluna contem valor). "
                        + "Gera um novo resultado reutilizavel.",
                JsonSchemas.object()
                        .required("resultado", JsonSchemas.string("Codigo do resultado, ex.: r2"))
                        .required("operacao", JsonSchemas.enumString(null, List.of("estatisticas", "agrupar",
                                "serie_temporal", "filtrar")))
                        .prop("dimensoes", JsonSchemas.arrayOfStrings("Colunas de agrupamento (agrupar)"))
                        .prop("metrica", JsonSchemas.string("Coluna numerica agregada (omitir para contar linhas)"))
                        .prop("agregacao", JsonSchemas.enumString(null, List.of("soma", "media", "contagem", "minimo",
                                "maximo", "mediana")))
                        .prop("coluna_data", JsonSchemas.string("Coluna de data (serie_temporal)"))
                        .prop("granularidade", JsonSchemas.enumString(null, List.of("dia", "semana", "mes", "ano")))
                        .prop("dimensao_serie", JsonSchemas.string("Dimensao adicional na serie (opcional)"))
                        .prop("coluna", JsonSchemas.string("Coluna do filtro (filtrar)"))
                        .prop("valor", JsonSchemas.string("Valor contido (filtrar)"))
                        .prop("titulo", JsonSchemas.string(null))
                        .build(),
                ToolKind.ANALYSIS, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Analisando " + Objects.toString(ToolArgs.text(arguments, "resultado"), "resultado");
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                ResultStore.StoredResult origem = store.exigir(context.conversaId(), ToolArgs.requiredText(arguments, "resultado"));
                String operacao = ToolArgs.requiredText(arguments, "operacao");
                TabularData saida = switch (operacao) {
                    case "estatisticas" -> TabularOps.estatisticas(origem.data());
                    case "agrupar" -> TabularOps.agrupar(origem.data(), ToolArgs.texts(arguments, "dimensoes"),
                            ToolArgs.text(arguments, "metrica"), ToolArgs.text(arguments, "agregacao"));
                    case "serie_temporal" -> TabularOps.serieTemporal(origem.data(),
                            ToolArgs.requiredText(arguments, "coluna_data"), ToolArgs.text(arguments, "granularidade"),
                            ToolArgs.text(arguments, "metrica"), ToolArgs.text(arguments, "agregacao"),
                            ToolArgs.text(arguments, "dimensao_serie"));
                    case "filtrar" -> TabularOps.filtrar(origem.data(), ToolArgs.requiredText(arguments, "coluna"),
                            ToolArgs.requiredText(arguments, "valor"));
                    default -> throw new ToolException("Operacao invalida: " + operacao);
                };
                String titulo = Optional.ofNullable(ToolArgs.text(arguments, "titulo"))
                        .orElse(origem.titulo() + " (" + operacao + ")");
                if (origem.data().truncated()) titulo = titulo + " [base truncada]";
                ResultStore.StoredResult novo = store.registrar(context.run(), name(), ResultadoAssistente.Natureza.CALCULO,
                        titulo, saida, Map.of("origem", origem.codigo(), "operacao", operacao));
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("resultado_origem", origem.codigo());
                if (origem.data().truncated()) {
                    extra.put("aviso", "O resultado de origem estava truncado; a analise considera apenas as linhas salvas.");
                }
                return formatter.toToolResult(novo, properties.getAgent().getToolResultMaxChars(), extra, null);
            }
        };
    }

    @Bean
    AgentTool compararResultadosTool(ResultStore store, ResultFormatter formatter, AssistenteProperties properties) {
        return new BaseTool("comparar_resultados", "Conciliar resultados",
                "Concilia dois resultados pela chave (ex.: planilha enviada x movimentacoes do Nexo): aponta linhas que "
                        + "CONFEREM, DIVERGENTES (diferenca acima da tolerancia) e presentes SOMENTE em um dos lados. "
                        + "Chaves sao comparadas sem diferenciar maiusculas, acentos e espacos. Linhas repetidas da mesma "
                        + "chave sao somadas antes da comparacao.",
                JsonSchemas.object()
                        .required("resultado_a", JsonSchemas.string("Codigo do primeiro resultado"))
                        .required("chave_a", JsonSchemas.string("Coluna chave no resultado A"))
                        .required("metricas_a", JsonSchemas.arrayOfStrings("Colunas numericas comparadas em A"))
                        .required("resultado_b", JsonSchemas.string("Codigo do segundo resultado"))
                        .prop("chave_b", JsonSchemas.string("Coluna chave em B (padrao: mesma de A)"))
                        .prop("metricas_b", JsonSchemas.arrayOfStrings("Colunas equivalentes em B (mesma ordem)"))
                        .prop("rotulo_a", JsonSchemas.string("Nome curto do lado A, ex.: planilha"))
                        .prop("rotulo_b", JsonSchemas.string("Nome curto do lado B, ex.: ces"))
                        .prop("tolerancia", JsonSchemas.number("Diferenca absoluta tolerada (padrao 0)"))
                        .prop("titulo", JsonSchemas.string(null))
                        .build(),
                ToolKind.ANALYSIS, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Conciliando " + Objects.toString(ToolArgs.text(arguments, "resultado_a"), "A") + " com "
                        + Objects.toString(ToolArgs.text(arguments, "resultado_b"), "B");
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                ResultStore.StoredResult a = store.exigir(context.conversaId(), ToolArgs.requiredText(arguments, "resultado_a"));
                ResultStore.StoredResult b = store.exigir(context.conversaId(), ToolArgs.requiredText(arguments, "resultado_b"));
                String rotuloA = rotulo(ToolArgs.text(arguments, "rotulo_a"), "a");
                String rotuloB = rotulo(ToolArgs.text(arguments, "rotulo_b"), "b");
                Double tolerancia = ToolArgs.number(arguments, "tolerancia");
                TabularData conciliado = TabularOps.conciliar(a.data(), ToolArgs.requiredText(arguments, "chave_a"),
                        ToolArgs.texts(arguments, "metricas_a"), b.data(), ToolArgs.text(arguments, "chave_b"),
                        ToolArgs.texts(arguments, "metricas_b"), rotuloA, rotuloB, tolerancia == null ? 0 : tolerancia);
                Map<String, Long> contagem = new LinkedHashMap<>();
                conciliado.rows().forEach(row -> contagem.merge(String.valueOf(row.get(1)), 1L, Long::sum));
                String titulo = Optional.ofNullable(ToolArgs.text(arguments, "titulo"))
                        .orElse("Conciliacao " + a.codigo() + " x " + b.codigo());
                ResultStore.StoredResult novo = store.registrar(context.run(), name(), ResultadoAssistente.Natureza.CALCULO,
                        titulo, conciliado, Map.of("a", a.codigo(), "b", b.codigo()));
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("resumo_situacoes", contagem);
                if (a.data().truncated() || b.data().truncated()) {
                    extra.put("aviso", "Um dos resultados estava truncado; itens podem aparecer como ausentes por isso.");
                }
                return formatter.toToolResult(novo, properties.getAgent().getToolResultMaxChars(), extra,
                        contagem.getOrDefault("DIVERGENTE", 0L) + " divergencias");
            }
        };
    }

    @Bean
    AgentTool detectarAnomaliasTool(ResultStore store, ResultFormatter formatter, AssistenteProperties properties) {
        return new BaseTool("detectar_anomalias", "Detectar anomalias",
                "Detecta valores fora do padrao numa coluna numerica de um resultado (metodo 'iqr' de Tukey, padrao, ou "
                        + "'zscore'). Para anomalias por grupo (ex.: material, equipe, dia), agregue antes com "
                        + "analisar_resultado ou consulte ja agrupado. Retorna as linhas anomalas com z-score.",
                JsonSchemas.object()
                        .required("resultado", JsonSchemas.string("Codigo do resultado"))
                        .required("metrica", JsonSchemas.string("Coluna numerica analisada"))
                        .prop("metodo", JsonSchemas.enumString(null, List.of("iqr", "zscore")))
                        .prop("limite_z", JsonSchemas.number("Limite de |z| para zscore (padrao 2.5)"))
                        .prop("titulo", JsonSchemas.string(null))
                        .build(),
                ToolKind.ANALYSIS, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Procurando comportamentos anormais";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                ResultStore.StoredResult origem = store.exigir(context.conversaId(), ToolArgs.requiredText(arguments, "resultado"));
                String metrica = ToolArgs.requiredText(arguments, "metrica");
                String metodo = Optional.ofNullable(ToolArgs.text(arguments, "metodo")).orElse("iqr");
                Double limite = ToolArgs.number(arguments, "limite_z");
                TabularData anomalias = TabularOps.anomalias(origem.data(), metrica, metodo, limite == null ? 2.5 : limite);
                String titulo = Optional.ofNullable(ToolArgs.text(arguments, "titulo"))
                        .orElse("Anomalias em " + metrica + " (" + origem.codigo() + ")");
                ResultStore.StoredResult novo = store.registrar(context.run(), name(), ResultadoAssistente.Natureza.CALCULO,
                        titulo, anomalias, Map.of("origem", origem.codigo(), "metodo", metodo));
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("distribuicao", TabularOps.resumoDistribuicao(origem.data(), metrica));
                extra.put("metodo", metodo);
                return formatter.toToolResult(novo, properties.getAgent().getToolResultMaxChars(), extra,
                        anomalias.totalRows() + " anomalias");
            }
        };
    }

    private static String rotulo(String valor, String padrao) {
        String base = valor == null || valor.isBlank() ? padrao : valor;
        String normalizado = TabularOps.normalizar(base).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return normalizado.isBlank() ? padrao : normalizado;
    }
}
