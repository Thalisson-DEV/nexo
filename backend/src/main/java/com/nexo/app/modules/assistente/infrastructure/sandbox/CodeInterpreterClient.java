package com.nexo.app.modules.assistente.infrastructure.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Cliente do servico de sandbox Python do Onyx (imagem {@code onyxdotapp/code-interpreter}), portado de
 * {@code onyx/tools/tool_implementations/python/code_interpreter_client.py}. Mesmo contrato HTTP:
 * {@code GET /health}, {@code POST /v1/files}, {@code GET|DELETE /v1/files/{id}},
 * {@code POST /v1/execute} e {@code POST /v1/execute/stream} (SSE: output/result/error).
 * O sandbox nao tem acesso a rede, ao banco, a segredos nem ao sistema de arquivos do Nexo.
 */
@Component
public class CodeInterpreterClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeInterpreterClient.class);
    private static final Duration HEALTH_CACHE = Duration.ofSeconds(30);

    private final AssistenteProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Health cachedHealth;
    private volatile Instant healthCheckedAt = Instant.EPOCH;

    public CodeInterpreterClient(AssistenteProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return properties.getCodeInterpreter().isConfigured();
    }

    public Health health() {
        if (!configured()) return new Health(false, "Sandbox Python nao configurado (CODE_INTERPRETER_BASE_URL)", "0.0.0");
        if (cachedHealth != null && Duration.between(healthCheckedAt, Instant.now()).compareTo(HEALTH_CACHE) < 0) {
            return cachedHealth;
        }
        Health result;
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                result = new Health(true, "Sandbox respondeu HTTP " + response.statusCode(), "0.0.0");
            } else {
                JsonNode body = objectMapper.readTree(response.body());
                boolean ok = "ok".equals(body.path("status").asText());
                result = new Health(true, ok ? "" : body.path("message").asText("erro desconhecido"),
                        body.path("version").asText("0.0.0"));
            }
        } catch (IOException exception) {
            result = new Health(false, "Sandbox Python inacessivel", "0.0.0");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result = new Health(false, "Verificacao interrompida", "0.0.0");
        }
        cachedHealth = result;
        healthCheckedAt = Instant.now();
        return result;
    }

    /** Forca nova verificacao de saude na proxima consulta (apos falha de comunicacao). */
    public void invalidarSaude() {
        cachedHealth = null;
        healthCheckedAt = Instant.EPOCH;
    }

    public String uploadFile(byte[] content, String filename) throws IOException, InterruptedException {
        String boundary = "----ces" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 512);
        String safeName = filename.replace("\"", "_").replace("\r", "_").replace("\n", "_");
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + safeName
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/v1/files"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(), HttpResponse.BodyHandlers.ofString());
        exigirSucesso(response.statusCode(), "upload");
        return objectMapper.readTree(response.body()).path("file_id").asText();
    }

    public byte[] downloadFile(String fileId) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder(uri("/v1/files/" + encode(fileId)))
                .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        exigirSucesso(response.statusCode(), "download");
        return response.body();
    }

    public void deleteFile(String fileId) {
        try {
            httpClient.send(HttpRequest.newBuilder(uri("/v1/files/" + encode(fileId)))
                    .timeout(Duration.ofSeconds(10)).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException exception) {
            LOGGER.debug("Falha ao remover arquivo do sandbox: {}", exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Executa codigo com saida em streaming (SSE). Recai para execucao em lote se o servidor for antigo.
     */
    public ExecuteResult executeStreaming(String code, int timeoutMs, List<StagedFile> files, OutputListener listener,
            BooleanSupplier cancelled) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", code);
        payload.put("timeout_ms", timeoutMs);
        if (files != null && !files.isEmpty()) {
            var array = payload.putArray("files");
            files.forEach(file -> array.addObject().put("path", file.path()).put("file_id", file.fileId()));
        }
        String json = objectMapper.writeValueAsString(payload);
        Duration httpTimeout = Duration.ofMillis(timeoutMs + 10_000L);
        HttpResponse<InputStream> response = httpClient.send(HttpRequest.newBuilder(uri("/v1/execute/stream"))
                .timeout(httpTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 404) {
            response.body().close();
            return executeBatch(json, httpTimeout, listener);
        }
        if (response.statusCode() >= 400) {
            String erro = new String(response.body().readNBytes(2048), StandardCharsets.UTF_8);
            response.body().close();
            throw new IOException("Sandbox respondeu HTTP " + response.statusCode() + ": " + erro);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.getAsBoolean()) throw new IOException("Execucao cancelada pelo usuario");
                if (line.isEmpty()) {
                    if (event != null && !data.isEmpty()) {
                        ExecuteResult result = handleEvent(event, data.toString(), listener);
                        if (result != null) return result;
                    }
                    event = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).trim());
                }
            }
            if (event != null && !data.isEmpty()) {
                ExecuteResult result = handleEvent(event, data.toString(), listener);
                if (result != null) return result;
            }
        }
        throw new IOException("O sandbox encerrou o stream sem resultado");
    }

    private ExecuteResult handleEvent(String event, String data, OutputListener listener) throws IOException {
        JsonNode node = objectMapper.readTree(data);
        return switch (event) {
            case "output" -> {
                listener.onOutput(node.path("stream").asText("stdout"), node.path("data").asText(""));
                yield null;
            }
            case "result" -> toResult(node, null, null);
            case "error" -> throw new IOException("Erro no sandbox: " + node.path("message").asText("desconhecido"));
            default -> null;
        };
    }

    private ExecuteResult executeBatch(String json, Duration timeout, OutputListener listener)
            throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/v1/execute"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
        exigirSucesso(response.statusCode(), "execute");
        JsonNode node = objectMapper.readTree(response.body());
        String stdout = node.path("stdout").asText("");
        String stderr = node.path("stderr").asText("");
        if (!stdout.isEmpty()) listener.onOutput("stdout", stdout);
        if (!stderr.isEmpty()) listener.onOutput("stderr", stderr);
        return toResult(node, stdout, stderr);
    }

    private static ExecuteResult toResult(JsonNode node, String stdout, String stderr) {
        List<WorkspaceFile> files = new ArrayList<>();
        for (JsonNode file : node.path("files")) {
            files.add(new WorkspaceFile(file.path("path").asText(), file.path("kind").asText("file"),
                    file.hasNonNull("file_id") ? file.get("file_id").asText() : null));
        }
        Integer exitCode = node.hasNonNull("exit_code") ? node.get("exit_code").asInt() : null;
        return new ExecuteResult(exitCode, node.path("timed_out").asBoolean(false), node.path("duration_ms").asLong(0),
                files, stdout, stderr);
    }

    private URI uri(String path) {
        String base = properties.getCodeInterpreter().getBaseUrl().trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void exigirSucesso(int status, String operacao) throws IOException {
        if (status < 200 || status >= 300) throw new IOException("Sandbox falhou em " + operacao + " (HTTP " + status + ")");
    }

    public record Health(boolean connected, String error, String version) {
        public boolean healthy() {
            return connected && (error == null || error.isBlank());
        }
    }

    public record StagedFile(String path, String fileId) {
    }

    public record WorkspaceFile(String path, String kind, String fileId) {
    }

    public record ExecuteResult(Integer exitCode, boolean timedOut, long durationMs, List<WorkspaceFile> files,
            String stdout, String stderr) {
    }

    @FunctionalInterface
    public interface OutputListener {
        void onOutput(String stream, String data);
    }
}
