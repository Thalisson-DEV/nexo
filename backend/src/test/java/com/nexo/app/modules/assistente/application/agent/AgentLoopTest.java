package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.llm.*;
import com.nexo.app.modules.assistente.infrastructure.persistence.AcaoPendenteAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ExecucaoFerramentaAssistenteRepository;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AssistenteProperties properties = new AssistenteProperties();
    private final List<LlmRequest> requisicoes = new ArrayList<>();
    private final Deque<Function<LlmRequest, LlmResponse>> roteiro = new ArrayDeque<>();
    private ExecucaoFerramentaAssistenteRepository auditoria;
    private AgentRun run;
    private AgentRunContext contexto;

    @BeforeEach
    void setUp() {
        properties.getAgent().setMaxCycles(6);
        properties.getAgent().setMaxConsecutiveToolFailures(2);
        auditoria = mock(ExecucaoFerramentaAssistenteRepository.class);
        run = new AgentRun(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                packet -> {
                    try {
                        return mapper.writeValueAsString(packet);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
        contexto = new AgentRunContext(run, new SemanticQueryContext(run.usuarioId(), Set.of("estoque:saldo:read")), "Ana",
                null, List.of(), Duration.ofMinutes(5), 0, 0, 0);
    }

    @Test
    void executaFerramentaEDevolveResultadoAoModeloAteARespostaFinal() {
        roteiro.add(request -> chamada("call_1", "somar", "{\"valores\":[1,2]}"));
        roteiro.add(request -> texto(request, "O total e 3 (r1)."));

        AgentLoop.Outcome resultado = loop(somar()).executar(contexto, mensagensIniciais(), List.of(somar()));

        assertThat(resultado.status()).isEqualTo(AgentLoop.Status.CONCLUIDO);
        assertThat(resultado.texto()).isEqualTo("O total e 3 (r1).");
        assertThat(resultado.ciclos()).isEqualTo(2);
        List<LlmMessage> segunda = requisicoes.get(1).messages();
        assertThat(segunda.get(2)).isInstanceOfSatisfying(LlmMessage.Assistant.class,
                assistente -> assertThat(assistente.toolCalls()).extracting(LlmToolCall::name).containsExactly("somar"));
        assertThat(segunda.get(3)).isInstanceOfSatisfying(LlmMessage.Tool.class,
                ferramenta -> assertThat(ferramenta.content()).contains("\"total\":3"));
        List<String> tipos = run.snapshotPackets().stream().map(Packet::type).toList();
        assertThat(tipos).containsSubsequence(Packet.Types.TOOL_START, Packet.Types.TOOL_RESULT,
                Packet.Types.MESSAGE_START, Packet.Types.MESSAGE_DELTA, Packet.Types.SECTION_END);
        Packet toolStart = run.snapshotPackets().stream().filter(p -> p.type().equals(Packet.Types.TOOL_START)).findFirst().orElseThrow();
        assertThat(toolStart.placement()).isEqualTo(new Packet.Placement(1, 1));
        assertThat(toolStart.obj().get("rotulo")).isEqualTo("Somando 2 valores");
        assertThat(contexto.chamadasFerramenta()).isEqualTo(1);
    }

    @Test
    void falhasConsecutivasForcamRespostaSemFerramentas() {
        for (int i = 0; i < 5; i++) {
            roteiro.add(request -> request.toolChoice() == LlmRequest.ToolChoice.NONE
                    ? texto(request, "Nao consegui obter os dados.")
                    : chamada("call_" + UUID.randomUUID(), "falhar", "{}"));
        }

        AgentLoop.Outcome resultado = loop(falhar()).executar(contexto, mensagensIniciais(), List.of(falhar()));

        assertThat(resultado.status()).isEqualTo(AgentLoop.Status.CONCLUIDO);
        assertThat(resultado.texto()).isEqualTo("Nao consegui obter os dados.");
        assertThat(requisicoes).hasSize(3);
        assertThat(requisicoes.get(2).toolChoice()).isEqualTo(LlmRequest.ToolChoice.NONE);
        assertThat(requisicoes.get(2).messages()).anySatisfy(mensagem ->
                assertThat(mensagem).isInstanceOf(LlmMessage.System.class)
                        .extracting(LlmMessage::content).asString().contains("Limite de etapas"));
    }

    @Test
    void ferramentaNaoOferecidaVoltaComoErroParaOModelo() {
        roteiro.add(request -> chamada("call_x", "apagar_tudo", "{}"));
        roteiro.add(request -> texto(request, "Nao posso fazer isso."));

        AgentLoop.Outcome resultado = loop(somar()).executar(contexto, mensagensIniciais(), List.of(somar()));

        assertThat(resultado.texto()).isEqualTo("Nao posso fazer isso.");
        LlmMessage.Tool retorno = (LlmMessage.Tool) requisicoes.get(1).messages().get(3);
        assertThat(retorno.content()).contains("Ferramenta inexistente");
    }

    @Test
    void argumentosInvalidosNaoDerrubamOLoop() {
        roteiro.add(request -> chamada("call_1", "somar", "{valores: [1,"));
        roteiro.add(request -> texto(request, "Corrigido."));

        loop(somar()).executar(contexto, mensagensIniciais(), List.of(somar()));

        LlmMessage.Tool retorno = (LlmMessage.Tool) requisicoes.get(1).messages().get(3);
        assertThat(retorno.content()).contains("Argumentos JSON invalidos");
    }

    @Test
    void cancelamentoInterrompeAntesDoProximoCiclo() {
        run.requestCancel();
        AgentLoop.Outcome resultado = loop(somar()).executar(contexto, mensagensIniciais(), List.of(somar()));
        assertThat(resultado.status()).isEqualTo(AgentLoop.Status.CANCELADO);
        assertThat(requisicoes).isEmpty();
    }

    // ------------------------------------------------------------------------------------ apoio

    private AgentLoop loop(AgentTool... tools) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        for (AgentTool tool : tools) factory.addBean(tool.name(), tool);
        ToolRegistry registry = new ToolRegistry(factory.getBeanProvider(AgentTool.class),
                factory.getBeanProvider(AgentToolProvider.class));
        ToolExecutor executor = new ToolExecutor(registry, mapper, properties, auditoria,
                mock(AcaoPendenteAssistenteRepository.class), factory.getBeanProvider(MeterRegistry.class));
        return new AgentLoop(new RoteiroLlm(), executor, properties);
    }

    private List<LlmMessage> mensagensIniciais() {
        return List.of(new LlmMessage.System("Voce e o Copiloto"), new LlmMessage.User("Some 1 e 2"));
    }

    private static AgentTool somar() {
        return new BaseTool("somar", "Somar", "Soma valores", JsonSchemas.object()
                .required("valores", JsonSchemas.array(null, JsonSchemas.number(null))).build(),
                ToolKind.ANALYSIS, Set.of(), Duration.ofSeconds(5)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Somando " + arguments.path("valores").size() + " valores";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                double total = 0;
                for (JsonNode valor : arguments.path("valores")) total += valor.asDouble();
                return ToolResult.of("{\"total\":" + (long) total + "}", "Total calculado").withResult("r1", 1, Map.of());
            }
        };
    }

    private static AgentTool falhar() {
        return new BaseTool("falhar", "Falhar", "Sempre falha", JsonSchemas.object().build(), ToolKind.DATA, Set.of(),
                Duration.ofSeconds(5)) {
            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                throw new ToolException("Fonte temporariamente indisponivel");
            }
        };
    }

    private static LlmResponse chamada(String id, String nome, String argumentos) {
        return new LlmResponse("", "pensando", List.of(new LlmToolCall(id, nome, argumentos)), "tool_calls",
                new LlmResponse.LlmUsage(100, 10, 0, 5), "modelo-teste");
    }

    private static LlmResponse texto(LlmRequest request, String conteudo) {
        return new LlmResponse(conteudo, null, List.of(), "stop", new LlmResponse.LlmUsage(120, 20, 0, 0), "modelo-teste");
    }

    private final class RoteiroLlm implements LlmProvider {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String defaultModel() {
            return "modelo-teste";
        }

        @Override
        public LlmResponse stream(LlmRequest request, LlmStreamListener listener, BooleanSupplier cancelled) {
            requisicoes.add(request);
            LlmResponse resposta = roteiro.isEmpty() ? texto(request, "fim") : roteiro.poll().apply(request);
            if (!resposta.content().isEmpty()) {
                for (String parte : resposta.content().split("(?<= )")) listener.onContentDelta(parte);
            }
            return resposta;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            return stream(request, LlmStreamListener.NOOP, () -> false);
        }
    }
}
