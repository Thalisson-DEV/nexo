package com.nexo.app.modules.sst.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.sst.application.port.NormalizacaoTranscricaoGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SpringAiNormalizacaoTranscricaoGateway implements NormalizacaoTranscricaoGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiNormalizacaoTranscricaoGateway.class);
    private static final int LIMITE_RESPOSTA = 60_000;

    private final ObjectProvider<ChatClient.Builder> builderProvider;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration timeout;
    private final String versaoModelo;
    private final String modelName;

    public SpringAiNormalizacaoTranscricaoGateway(
            ObjectProvider<ChatClient.Builder> builderProvider,
            ObjectMapper objectMapper,
            @Value("${ces.sst.ata-ai.normalizacao.enabled:false}") boolean enabled,
            @Value("${ces.sst.ata-ai.normalizacao.timeout-seconds:30}") long timeoutSeconds,
            @Value("${ces.sst.ata-ai.normalizacao.model-version:normalizacao-v1}") String versaoModelo,
            @Value("${ces.sst.ata-ai.normalizacao.model-name:}") String modelName) {
        this.builderProvider = builderProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.versaoModelo = versaoModelo;
        this.modelName = modelName;
    }

    @Override
    public Resultado normalizar(Entrada entrada) {
        if (!enabled) return Resultado.falha("NORMALIZADOR_DESABILITADO");
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        if (builder == null) return Resultado.falha("PROVEDOR_NAO_CONFIGURADO");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            String resposta = executor.submit(() -> {
                var prompt = builder.build().prompt().system(promptSistema())
                        .user(entrada.transcricaoConfirmada());
                if (StringUtils.hasText(modelName)) {
                    prompt.options(OpenAiChatOptions.builder()
                            .model(modelName.trim()).temperature(0.1));
                }
                return prompt.call().content();
            })
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (resposta == null || resposta.isBlank() || resposta.length() > LIMITE_RESPOSTA) {
                return Resultado.falha("RESPOSTA_INVALIDA");
            }
            Conteudo conteudo = objectMapper.readValue(removerCerca(resposta), Conteudo.class);
            if (!valido(conteudo, entrada.transcricaoConfirmada())) {
                return Resultado.falha("SCHEMA_OU_ORIGEM_INVALIDA");
            }
            return Resultado.sucesso(conteudo);
        } catch (TimeoutException e) {
            LOGGER.warn("Timeout na normalização de transcrição SST");
            return Resultado.falha("TIMEOUT_PROVEDOR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("NORMALIZACAO_INTERROMPIDA");
        } catch (Exception e) {
            LOGGER.warn("Falha na normalização de transcrição SST: {}", e.getClass().getSimpleName());
            return Resultado.falha("PROVEDOR_OU_SCHEMA_INDISPONIVEL");
        }
    }

    public String versaoModelo() {
        return StringUtils.hasText(modelName) ? modelName.trim() + ":" + versaoModelo : versaoModelo;
    }

    private static String promptSistema() {
        return """
                Organize uma transcrição de reunião em JSON estrito, sem Markdown, com as chaves:
                topicos, decisoesCandidatas, acoesCandidatas e incertezas. Cada item deve ter exatamente:
                texto, trechoOrigem e requerConfirmacao. Use somente fatos presentes no texto. trechoOrigem deve
                copiar um trecho curto e literal da transcrição. Não decida presença, justificativa, quórum,
                responsável, prazo ou conformidade. Se houver dúvida, coloque em incertezas e marque
                requerConfirmacao=true. Decisões e ações são candidatas até confirmação humana.
                """;
    }

    private static boolean valido(Conteudo conteudo, String origem) {
        if (conteudo == null) return false;
        return java.util.stream.Stream.of(conteudo.topicos(), conteudo.decisoesCandidatas(),
                        conteudo.acoesCandidatas(), conteudo.incertezas())
                .flatMap(java.util.Collection::stream)
                .allMatch(item -> item != null && item.texto() != null && !item.texto().isBlank()
                        && item.trechoOrigem() != null && !item.trechoOrigem().isBlank()
                        && origem.contains(item.trechoOrigem()));
    }

    private static String removerCerca(String valor) {
        String texto = valor.trim();
        if (texto.startsWith("```")) {
            int primeiraQuebra = texto.indexOf('\n');
            int ultimaCerca = texto.lastIndexOf("```");
            if (primeiraQuebra > 0 && ultimaCerca > primeiraQuebra) {
                texto = texto.substring(primeiraQuebra + 1, ultimaCerca).trim();
            }
        }
        return texto;
    }
}
