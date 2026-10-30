package com.nexo.app.modules.assistente.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.llm.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato de streaming observado na Fireworks (DeepSeek V4 Flash): raciocinio em delta.reasoning_content,
 * chamadas de ferramenta fragmentadas por indice, finish_reason tool_calls, chunk final so com usage e [DONE].
 */
class OpenAiCompatibleLlmProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> corpos = new CopyOnWriteArrayList<>();
    private final AtomicInteger chamadas = new AtomicInteger();
    private HttpServer server;
    private volatile int statusInicial = 200;
    private volatile int falhasAntesDoSucesso = 0;

    @BeforeEach
    void iniciar() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode corpo = mapper.readTree(exchange.getRequestBody().readAllBytes());
            corpos.add(corpo);
            int tentativa = chamadas.incrementAndGet();
            if ("modelo-fora-do-ar".equals(corpo.path("model").asText())) {
                byte[] erro = "{\"error\":{\"message\":\"Model not found, inaccessible, and/or not deployed\",\"code\":\"NOT_FOUND\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, erro.length);
                exchange.getResponseBody().write(erro);
                exchange.close();
                return;
            }
            if (tentativa <= falhasAntesDoSucesso) {
                byte[] erro = "{\"error\":{\"message\":\"sobrecarga\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusInicial, erro.length);
                exchange.getResponseBody().write(erro);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String chunk : List.of(
                        "{\"model\":\"accounts/fireworks/models/deepseek-v4-flash-0731\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"Preciso consultar\"}}]}",
                        "{\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\" o estoque.\"}}]}",
                        "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\",\"type\":\"function\",\"function\":{\"name\":\"consultar_estoque\",\"arguments\":\"\"}}]}}]}",
                        "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"material\\\":\"}}]}}]}",
                        "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"cabo\\\"}\"}}]}}]}",
                        "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"call_b\",\"function\":{\"name\":\"obter_indicadores\",\"arguments\":\"{}\"}}]}}]}",
                        "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                        "{\"choices\":[],\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":80,\"completion_tokens_details\":{\"reasoning_tokens\":20}}}")) {
                    out.write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    @AfterEach
    void parar() {
        server.stop(0);
    }

    @Test
    void agregaRaciocinioChamadasFragmentadasEUsoSemExibirRaciocinioComoTexto() {
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties(), mapper);
        List<String> textos = new ArrayList<>();
        List<String> ferramentasIniciadas = new ArrayList<>();
        LlmResponse resposta = provider.stream(requisicao(), new LlmStreamListener() {
            @Override
            public void onContentDelta(String delta) {
                textos.add(delta);
            }

            @Override
            public void onToolCallStarted(int index, String name) {
                ferramentasIniciadas.add(name);
            }
        }, () -> false);

        assertThat(textos).isEmpty();
        assertThat(resposta.reasoningContent()).isEqualTo("Preciso consultar o estoque.");
        assertThat(resposta.toolCalls()).containsExactly(
                new LlmToolCall("call_a", "consultar_estoque", "{\"material\":\"cabo\"}"),
                new LlmToolCall("call_b", "obter_indicadores", "{}"));
        assertThat(ferramentasIniciadas).containsExactly("consultar_estoque", "obter_indicadores");
        assertThat(resposta.finishReason()).isEqualTo("tool_calls");
        assertThat(resposta.usage().promptTokens()).isEqualTo(1200);
        assertThat(resposta.usage().reasoningTokens()).isEqualTo(20);
        assertThat(resposta.model()).isEqualTo("accounts/fireworks/models/deepseek-v4-flash-0731");
    }

    @Test
    void enviaFerramentasHistoricoComRaciocinioDoTurnoEToolChoice() {
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties(), mapper);
        provider.stream(requisicao(), LlmStreamListener.NOOP, () -> false);

        JsonNode corpo = corpos.getFirst();
        assertThat(corpo.path("stream").asBoolean()).isTrue();
        assertThat(corpo.path("stream_options").path("include_usage").asBoolean()).isTrue();
        assertThat(corpo.path("tools").get(0).path("function").path("name").asText()).isEqualTo("consultar_estoque");
        assertThat(corpo.path("tool_choice").asText()).isEqualTo("auto");
        JsonNode assistente = corpo.path("messages").get(2);
        assertThat(assistente.path("role").asText()).isEqualTo("assistant");
        assertThat(assistente.path("reasoning_content").asText()).isEqualTo("raciocinio anterior");
        assertThat(assistente.path("tool_calls").get(0).path("id").asText()).isEqualTo("call_0");
        JsonNode ferramenta = corpo.path("messages").get(3);
        assertThat(ferramenta.path("role").asText()).isEqualTo("tool");
        assertThat(ferramenta.path("tool_call_id").asText()).isEqualTo("call_0");
    }

    @Test
    void repeteFalhasTransitoriasAntesDoPrimeiroByte() {
        statusInicial = 503;
        falhasAntesDoSucesso = 1;
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties(), mapper);
        LlmResponse resposta = provider.stream(requisicao(), LlmStreamListener.NOOP, () -> false);
        assertThat(resposta.toolCalls()).hasSize(2);
        assertThat(chamadas.get()).isEqualTo(2);
    }

    @Test
    void naoRepeteErroDefinitivoDoProvedor() {
        statusInicial = 400;
        falhasAntesDoSucesso = 5;
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties(), mapper);
        assertThatThrownBy(() -> provider.stream(requisicao(), LlmStreamListener.NOOP, () -> false))
                .isInstanceOf(LlmException.class).hasMessageContaining("HTTP 400").hasMessageContaining("sobrecarga");
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test
    void usaModeloReservaQuandoOPrincipalSaiDoArNoProvedor() {
        AssistenteProperties properties = properties();
        properties.getLlm().setModel("modelo-fora-do-ar");
        properties.getLlm().setFallbackModel("modelo-reserva");
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(properties, mapper);
        LlmRequest principal = new LlmRequest("modelo-fora-do-ar", requisicao().messages(), requisicao().tools(),
                LlmRequest.ToolChoice.AUTO, 0.1, 500, true, null);

        assertThat(provider.stream(principal, LlmStreamListener.NOOP, () -> false).toolCalls()).hasSize(2);
        assertThat(corpos).extracting(corpo -> corpo.path("model").asText()).containsExactly("modelo-fora-do-ar", "modelo-reserva");

        provider.stream(principal, LlmStreamListener.NOOP, () -> false);
        assertThat(corpos.getLast().path("model").asText()).isEqualTo("modelo-reserva");
        assertThat(chamadas.get()).isEqualTo(3);
    }

    private LlmRequest requisicao() {
        LlmToolDefinition ferramenta = new LlmToolDefinition("consultar_estoque", "Consulta saldos",
                mapper.createObjectNode().put("type", "object"));
        return new LlmRequest("accounts/fireworks/models/deepseek-v4-flash-0731", List.of(
                new LlmMessage.System("Voce e o Copiloto"),
                new LlmMessage.User("Quanto temos de cabo?"),
                new LlmMessage.Assistant(null, "raciocinio anterior",
                        List.of(new LlmToolCall("call_0", "consultar_estoque", "{}"))),
                new LlmMessage.Tool("call_0", "consultar_estoque", "{\"linhas\":[]}")),
                List.of(ferramenta), LlmRequest.ToolChoice.AUTO, 0.1, 500, true, null);
    }

    private AssistenteProperties properties() {
        AssistenteProperties properties = new AssistenteProperties();
        properties.setEnabled(true);
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.getLlm().setApiKey("teste");
        properties.getLlm().setMaxRetries(2);
        properties.getLlm().setFirstTokenTimeoutSeconds(10);
        properties.getLlm().setStreamIdleTimeoutSeconds(10);
        return properties;
    }
}
