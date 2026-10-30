package com.nexo.app.modules.assistente.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.llm.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Adaptador para qualquer API compativel com OpenAI Chat Completions (Fireworks por padrao).
 *
 * <p>Porta do tratamento de stream do Onyx ({@code run_llm_step_pkt_generator}): acumula
 * {@code delta.content}, {@code delta.reasoning_content} e {@code delta.tool_calls} por indice; faz
 * retentativa somente antes do primeiro chunk; corta o stream se o provedor ficar mudo alem do limite
 * ou se o usuario cancelar.</p>
 */
@Component
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OpenAiCompatibleLlmProvider.class);

    private final AssistenteProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService watchdog;
    private volatile long primarioIndisponivelAte;

    public OpenAiCompatibleLlmProvider(AssistenteProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getLlm().getConnectTimeoutSeconds())))
                .build();
        this.watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "copiloto-llm-watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean isConfigured() {
        return properties.getLlm().isConfigured();
    }

    @Override
    public String defaultModel() {
        return properties.getLlm().getModel();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        ensureConfigured();
        ObjectNode body = buildBody(request, false);
        HttpResponse<String> response = sendWithRetry(body, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                () -> false);
        JsonNode root = readTree(response.body());
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) throw providerError(response.statusCode(), error);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            calls.add(new LlmToolCall(call.path("id").asText(null), call.path("function").path("name").asText(""),
                    call.path("function").path("arguments").asText("")));
        }
        return new LlmResponse(text(message.get("content")), text(message.get("reasoning_content")), calls,
                choice.path("finish_reason").asText(null), usage(root.get("usage")), root.path("model").asText(request.model()));
    }

    @Override
    public LlmResponse stream(LlmRequest request, LlmStreamListener listener, BooleanSupplier cancelled) {
        ensureConfigured();
        LlmStreamListener sink = listener == null ? LlmStreamListener.NOOP : listener;
        ObjectNode body = buildBody(request, true);
        HttpResponse<InputStream> response = sendWithRetry(body, HttpResponse.BodyHandlers.ofInputStream(), cancelled);

        StreamAccumulator accumulator = new StreamAccumulator(sink);
        AtomicLong lastActivity = new AtomicLong(System.nanoTime());
        AtomicBoolean aborted = new AtomicBoolean(false);
        InputStream bodyStream = response.body();
        long idleNanos = TimeUnit.SECONDS.toNanos(Math.max(5, properties.getLlm().getStreamIdleTimeoutSeconds()));
        ScheduledFuture<?> guard = watchdog.scheduleAtFixedRate(() -> {
            boolean idle = System.nanoTime() - lastActivity.get() > idleNanos;
            if ((cancelled.getAsBoolean() || idle) && aborted.compareAndSet(false, true)) {
                closeQuietly(bodyStream);
            }
        }, 250, 250, TimeUnit.MILLISECONDS);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastActivity.set(System.nanoTime());
                if (cancelled.getAsBoolean()) break;
                if (line.isBlank() || line.startsWith(":") || !line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                if (data.isEmpty()) continue;
                accumulator.accept(readTree(data), response.statusCode());
            }
        } catch (IOException exception) {
            if (!cancelled.getAsBoolean()) {
                if (aborted.get()) {
                    throw new LlmException("O provedor de IA ficou sem responder alem do limite configurado", 504, true, exception);
                }
                throw new LlmException("Conexao com o provedor de IA interrompida", 502, true, exception);
            }
        } finally {
            guard.cancel(false);
            closeQuietly(bodyStream);
        }
        return accumulator.toResponse(request.model());
    }

    // ------------------------------------------------------------------------------------ wire

    private ObjectNode buildBody(LlmRequest request, boolean stream) {
        AssistenteProperties.Llm config = properties.getLlm();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.model() == null || request.model().isBlank() ? config.getModel() : request.model());
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) messages.add(toWire(message));
        body.put("stream", stream);
        if (stream) body.putObject("stream_options").put("include_usage", true);
        body.put("temperature", request.temperature() == null ? config.getTemperature() : request.temperature());
        body.put("max_tokens", request.maxTokens() == null ? config.getMaxOutputTokens() : request.maxTokens());
        if (!request.tools().isEmpty()) {
            // Com ToolChoice.NONE as definicoes continuam no corpo (o historico pode conter tool_calls), mas o
            // modelo e instruido a responder em texto.
            ArrayNode tools = body.putArray("tools");
            for (LlmToolDefinition tool : request.tools()) {
                ObjectNode wrapper = tools.addObject();
                wrapper.put("type", "function");
                ObjectNode function = wrapper.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", tool.parameters());
            }
            body.put("tool_choice", switch (request.toolChoice()) {
                case REQUIRED -> "required";
                case NONE -> "none";
                case AUTO -> "auto";
            });
            if (request.toolChoice() != LlmRequest.ToolChoice.NONE) {
                boolean parallel = request.parallelToolCalls() == null ? config.isParallelToolCalls() : request.parallelToolCalls();
                body.put("parallel_tool_calls", parallel);
            }
        }
        String effort = request.reasoningEffort();
        if (effort != null && !effort.isBlank()) body.put("reasoning_effort", effort.trim());
        return body;
    }

    private ObjectNode toWire(LlmMessage message) {
        ObjectNode node = objectMapper.createObjectNode();
        switch (message) {
            case LlmMessage.System system -> {
                node.put("role", "system");
                node.put("content", system.content());
            }
            case LlmMessage.User user -> {
                node.put("role", "user");
                if (user.images().isEmpty()) {
                    node.put("content", user.content());
                } else {
                    ArrayNode parts = node.putArray("content");
                    parts.addObject().put("type", "text").put("text", user.content());
                    for (LlmMessage.Image image : user.images()) {
                        ObjectNode part = parts.addObject();
                        part.put("type", "image_url");
                        part.putObject("image_url").put("url", "data:" + image.mimeType() + ";base64," + image.base64());
                    }
                }
            }
            case LlmMessage.Assistant assistant -> {
                node.put("role", "assistant");
                if (assistant.content() == null || assistant.content().isEmpty()) {
                    node.putNull("content");
                } else {
                    node.put("content", assistant.content());
                }
                if (assistant.reasoningContent() != null && !assistant.reasoningContent().isBlank()) {
                    node.put("reasoning_content", assistant.reasoningContent());
                }
                if (!assistant.toolCalls().isEmpty()) {
                    ArrayNode calls = node.putArray("tool_calls");
                    for (LlmToolCall call : assistant.toolCalls()) {
                        ObjectNode wire = calls.addObject();
                        wire.put("id", call.id());
                        wire.put("type", "function");
                        wire.putObject("function").put("name", call.name())
                                .put("arguments", call.arguments() == null || call.arguments().isBlank() ? "{}" : call.arguments());
                    }
                }
            }
            case LlmMessage.Tool tool -> {
                node.put("role", "tool");
                node.put("tool_call_id", tool.toolCallId());
                if (tool.name() != null) node.put("name", tool.name());
                node.put("content", tool.content());
            }
        }
        return node;
    }

    private <T> HttpResponse<T> sendWithRetry(ObjectNode body, HttpResponse.BodyHandler<T> handler, BooleanSupplier cancelled) {
        AssistenteProperties.Llm config = properties.getLlm();
        aplicarModeloDisponivel(body);
        HttpRequest request = buildRequest(body);

        int attempts = Math.max(0, config.getMaxRetries()) + 1;
        LlmException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (cancelled.getAsBoolean()) throw new LlmException("Execucao cancelada", 499, false);
            try {
                HttpResponse<T> response = httpClient.send(request, handler);
                int status = response.statusCode();
                if (status >= 200 && status < 300) return response;
                String errorBody = drain(response.body());
                if (status == 404 && modeloIndisponivel(errorBody) && trocarParaReserva(body)) {
                    // O modelo principal saiu do ar no provedor: segue com o modelo reserva configurado.
                    request = buildRequest(body);
                    attempt--;
                    continue;
                }
                boolean retryable = status == 429 || status >= 500;
                last = new LlmException(describeHttpError(status, errorBody), status, retryable);
                if (!retryable || attempt == attempts) throw last;
                sleepBackoff(attempt, response.headers().firstValue("Retry-After").orElse(null));
            } catch (IOException exception) {
                last = new LlmException("Falha de comunicacao com o provedor de IA", 503, true, exception);
                if (attempt == attempts) throw last;
                sleepBackoff(attempt, null);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new LlmException("Chamada ao provedor interrompida", 499, false, exception);
            }
        }
        throw last == null ? new LlmException("Falha desconhecida no provedor", 500, false) : last;
    }

    private HttpRequest buildRequest(ObjectNode body) {
        AssistenteProperties.Llm config = properties.getLlm();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new LlmException("Falha ao serializar requisicao ao provedor", 0, false, exception);
        }
        return HttpRequest.newBuilder(URI.create(stripTrailingSlash(config.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(5, config.getFirstTokenTimeoutSeconds())))
                .header("Authorization", "Bearer " + config.getApiKey().trim())
                .header("Content-Type", "application/json")
                .header("Accept", body.path("stream").asBoolean() ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    /** Enquanto o modelo principal estiver marcado como fora do ar, usa direto o reserva (evita 404 a cada chamada). */
    private void aplicarModeloDisponivel(ObjectNode body) {
        String reserva = properties.getLlm().getFallbackModel();
        if (reserva == null || reserva.isBlank()) return;
        if (System.currentTimeMillis() < primarioIndisponivelAte
                && properties.getLlm().getModel().equals(body.path("model").asText())) {
            body.put("model", reserva.trim());
        }
    }

    private boolean trocarParaReserva(ObjectNode body) {
        String reserva = properties.getLlm().getFallbackModel();
        if (reserva == null || reserva.isBlank() || reserva.trim().equals(body.path("model").asText())) return false;
        LOGGER.warn("Modelo {} indisponivel no provedor; usando o modelo reserva {} pelos proximos 10 minutos",
                body.path("model").asText(), reserva.trim());
        primarioIndisponivelAte = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        body.put("model", reserva.trim());
        return true;
    }

    private static boolean modeloIndisponivel(String errorBody) {
        String texto = errorBody == null ? "" : errorBody.toLowerCase(Locale.ROOT);
        return texto.contains("model not found") || texto.contains("not deployed") || texto.contains("model_not_found")
                || texto.contains("does not exist");
    }

    private String describeHttpError(int status, String errorBody) {
        String detail = "";
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            JsonNode error = node.has("error") ? node.get("error") : node;
            detail = error.isTextual() ? error.asText() : error.path("message").asText("");
        } catch (Exception ignored) {
            detail = errorBody == null ? "" : errorBody;
        }
        detail = detail.replaceAll("\\s+", " ").trim();
        if (detail.length() > 300) detail = detail.substring(0, 300) + "...";
        return "Provedor de IA respondeu HTTP " + status + (detail.isEmpty() ? "" : ": " + detail);
    }

    private static String drain(Object body) {
        try {
            if (body instanceof String text) return text;
            if (body instanceof InputStream stream) {
                try (stream) {
                    byte[] bytes = stream.readNBytes(8192);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ignored) {
            // corpo de erro ilegivel: a mensagem generica e suficiente
        }
        return "";
    }

    private static void sleepBackoff(int attempt, String retryAfter) {
        long millis = (long) (1000 * Math.pow(2, attempt - 1));
        if (retryAfter != null) {
            try {
                millis = Math.max(millis, Long.parseLong(retryAfter.trim()) * 1000);
            } catch (NumberFormatException ignored) {
                // Retry-After em formato de data: usa o backoff exponencial
            }
        }
        try {
            Thread.sleep(Math.min(millis, 10_000));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new LlmException("Resposta invalida do provedor de IA", 502, true, exception);
        }
    }

    private LlmException providerError(int status, JsonNode error) {
        String message = error.isTextual() ? error.asText() : error.path("message").asText("erro do provedor");
        int code = status >= 400 ? status : 502;
        return new LlmException("Provedor de IA retornou erro: " + message, code, code == 429 || code >= 500);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new LlmException("Provedor de IA nao configurado", 503, false);
        }
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static LlmResponse.LlmUsage usage(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return LlmResponse.LlmUsage.EMPTY;
        int cached = node.path("prompt_tokens_details").path("cached_tokens").asInt(0);
        int reasoning = node.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
        return new LlmResponse.LlmUsage(node.path("prompt_tokens").asInt(0), node.path("completion_tokens").asInt(0),
                cached, reasoning);
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // o stream pode ja estar fechado pelo watchdog
        }
    }

    /** Acumula os chunks do stream (conteudo, raciocinio e chamadas de ferramenta por indice). */
    private final class StreamAccumulator {
        private final LlmStreamListener listener;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final TreeMap<Integer, ToolCallBuilder> calls = new TreeMap<>();
        private String finishReason;
        private String model;
        private LlmResponse.LlmUsage usage = LlmResponse.LlmUsage.EMPTY;

        private StreamAccumulator(LlmStreamListener listener) {
            this.listener = listener;
        }

        void accept(JsonNode chunk, int status) {
            JsonNode error = chunk.get("error");
            if (error != null && !error.isNull()) throw providerError(status, error);
            if (chunk.hasNonNull("model")) model = chunk.get("model").asText();
            if (chunk.hasNonNull("usage")) usage = usage(chunk.get("usage"));
            for (JsonNode choice : chunk.path("choices")) {
                JsonNode delta = choice.path("delta");
                String reasoningDelta = text(delta.get("reasoning_content"));
                if (reasoningDelta == null) reasoningDelta = text(delta.get("reasoning"));
                if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
                    reasoning.append(reasoningDelta);
                    listener.onReasoningDelta(reasoningDelta);
                }
                String contentDelta = text(delta.get("content"));
                if (contentDelta != null && !contentDelta.isEmpty()) {
                    content.append(contentDelta);
                    listener.onContentDelta(contentDelta);
                }
                for (JsonNode callDelta : delta.path("tool_calls")) {
                    int index = callDelta.path("index").asInt(calls.size());
                    ToolCallBuilder builder = calls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
                    if (callDelta.hasNonNull("id") && !callDelta.get("id").asText().isBlank()) {
                        builder.id = callDelta.get("id").asText();
                    }
                    JsonNode function = callDelta.path("function");
                    if (function.hasNonNull("name")) {
                        String name = function.get("name").asText();
                        boolean firstName = builder.name.isEmpty();
                        builder.name.append(name);
                        if (firstName && !name.isEmpty()) listener.onToolCallStarted(index, builder.name.toString());
                    }
                    if (function.hasNonNull("arguments")) builder.arguments.append(function.get("arguments").asText());
                }
                if (choice.hasNonNull("finish_reason")) finishReason = choice.get("finish_reason").asText();
            }
        }

        LlmResponse toResponse(String requestedModel) {
            List<LlmToolCall> toolCalls = new ArrayList<>();
            calls.forEach((index, builder) -> {
                if (builder.name.isEmpty()) return;
                String id = builder.id == null ? "call_" + UUID.randomUUID().toString().replace("-", "") : builder.id;
                toolCalls.add(new LlmToolCall(id, builder.name.toString(), builder.arguments.toString()));
            });
            return new LlmResponse(content.toString(), reasoning.isEmpty() ? null : reasoning.toString(), toolCalls,
                    finishReason, usage, model == null ? requestedModel : model);
        }
    }

    private static final class ToolCallBuilder {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }
}
