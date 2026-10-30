package com.nexo.app.modules.assistente.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Configuracao do Copiloto (prefixo {@code ces.assistente}). Todos os limites existem para proteger o
 * servidor e o usuario (loops, explosao de contexto, custo), nunca para reduzir artificialmente a
 * capacidade do agente: os padroes sao generosos e ajustaveis por variavel de ambiente.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ces.assistente")
public class AssistenteProperties {

    private boolean enabled;
    private int maxHistoryMessages = 40;
    private int timeoutSeconds = 45;
    private int discountMinSample = 10;
    private Llm llm = new Llm();
    private Agent agent = new Agent();
    private Sql sql = new Sql();
    private CodeInterpreter codeInterpreter = new CodeInterpreter();
    private WebSearch webSearch = new WebSearch();
    private Arquivos arquivos = new Arquivos();

    @Getter
    @Setter
    public static class Llm {
        private String baseUrl = "https://api.fireworks.ai/inference/v1";
        private String apiKey = "";
        private String model = "accounts/fireworks/models/deepseek-v4-flash-0731";
        /** Modelo reserva (opcional) usado quando o provedor informa que o principal nao esta disponivel. */
        private String fallbackModel = "";
        private String titleModel = "";
        private String reasoningEffort = "";
        private String titleReasoningEffort = "none";
        private double temperature = 0.1;
        private int maxOutputTokens = 6000;
        private int maxInputTokens = 120_000;
        private int connectTimeoutSeconds = 15;
        private int firstTokenTimeoutSeconds = 45;
        private int streamIdleTimeoutSeconds = 90;
        private int maxRetries = 2;
        private boolean parallelToolCalls = true;
        private boolean supportsVision;
        private BigDecimal inputCostPerMillionTokens = BigDecimal.ZERO;
        private BigDecimal outputCostPerMillionTokens = BigDecimal.ZERO;

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()
                    && !"disabled".equalsIgnoreCase(apiKey.trim()) && model != null && !model.isBlank();
        }

        public String effectiveTitleModel() {
            return titleModel == null || titleModel.isBlank() ? model : titleModel;
        }
    }

    @Getter
    @Setter
    public static class Agent {
        private int maxCycles = 16;
        private int maxToolCalls = 60;
        private int maxRunSeconds = 900;
        private int toolResultMaxChars = 24_000;
        private int maxConsecutiveToolFailures = 3;
        private int maxConcurrentRuns = 24;
    }

    @Getter
    @Setter
    public static class Sql {
        private boolean enabled = true;
        private String schema = "copiloto";
        private int statementTimeoutMs = 15_000;
        private int maxRows = 5_000;
        private int maxConcurrentQueries = 3;
        private String role = "";
    }

    @Getter
    @Setter
    public static class CodeInterpreter {
        private String baseUrl = "";
        private int timeoutMs = 60_000;
        private int maxOutputChars = 50_000;
        private int maxStagedFiles = 25;
        private long maxStagedBytes = 100L * 1024 * 1024;

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }

    @Getter
    @Setter
    public static class WebSearch {
        private String provider = "";
        private String apiKey = "";
        private String googleCx = "";
        private String searxngUrl = "";
        private int maxResults = 10;
        private boolean openUrlEnabled = true;
    }

    @Getter
    @Setter
    public static class Arquivos {
        private String armazenamento = "banco";
        private long maxBytes = 15L * 1024 * 1024;
        private int textoMaxChars = 400_000;
        private int planilhaMaxLinhas = 20_000;
    }
}
