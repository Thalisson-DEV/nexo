package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.llm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Loop agentic do Copiloto, portado de {@code run_llm_loop} do Onyx: a cada ciclo o modelo recebe o
 * historico e as ferramentas, transmite texto e/ou pede ferramentas; as ferramentas rodam em paralelo e
 * seus resultados voltam ao modelo ate ele responder sem chamar ferramentas. Protecoes: limite de ciclos,
 * de chamadas e de tempo (no ultimo ciclo as ferramentas sao desligadas e o modelo e instruido a responder),
 * deteccao de chamadas repetidas e de falhas consecutivas, cancelamento cooperativo.
 */
@Component
public class AgentLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentLoop.class);
    private static final int REPETICOES_MAXIMAS = 2;

    private final LlmProvider llm;
    private final ToolExecutor executor;
    private final AssistenteProperties properties;

    public AgentLoop(LlmProvider llm, ToolExecutor executor, AssistenteProperties properties) {
        this.llm = llm;
        this.executor = executor;
        this.properties = properties;
    }

    public Outcome executar(AgentRunContext run, List<LlmMessage> mensagens, List<AgentTool> ferramentas) {
        AssistenteProperties.Agent limites = properties.getAgent();
        AssistenteProperties.Llm config = properties.getLlm();
        List<LlmMessage> conversa = new ArrayList<>(mensagens);
        Map<String, AgentTool> porNome = new LinkedHashMap<>();
        ferramentas.forEach(tool -> porNome.put(tool.name(), tool));
        List<LlmToolDefinition> definicoes = ferramentas.stream()
                .map(tool -> new LlmToolDefinition(tool.name(), tool.description(), tool.inputSchema()))
                .toList();

        Map<String, Integer> assinaturas = new HashMap<>();
        int falhasSeguidas = 0;
        boolean forcarResposta = false;
        boolean tentativaExtraUsada = false;
        StringBuilder textoFinal = new StringBuilder();
        int ciclo = 0;
        int maxCiclos = Math.max(1, run.maxCiclos(limites.getMaxCycles()));

        while (ciclo < maxCiclos) {
            ciclo++;
            if (run.cancelled()) return Outcome.cancelado(textoFinal.toString(), ciclo - 1);
            boolean ultimo = ciclo == maxCiclos || forcarResposta
                    || run.chamadasFerramenta() >= run.maxChamadas(limites.getMaxToolCalls()) || run.tempoEsgotado(0.85);
            if (ultimo && !definicoes.isEmpty() && ciclo > 1) {
                conversa.add(new LlmMessage.System("Limite de etapas desta resposta atingido. Responda agora ao usuario, "
                        + "sem chamar ferramentas, com o que ja foi apurado; deixe claro o que ficou pendente e como continuar."));
            }
            int turno = ciclo;
            StringBuilder textoDoCiclo = new StringBuilder();
            boolean[] mensagemIniciada = {false};
            LlmStreamListener listener = new LlmStreamListener() {
                @Override
                public void onContentDelta(String delta) {
                    if (delta == null || delta.isEmpty()) return;
                    if (!mensagemIniciada[0]) {
                        mensagemIniciada[0] = true;
                        run.emit(Packet.of(new Packet.Placement(turno, 0), Packet.Types.MESSAGE_START));
                    }
                    textoDoCiclo.append(delta);
                    run.emit(Packet.of(new Packet.Placement(turno, 0), Packet.Types.MESSAGE_DELTA, "content", delta));
                }
            };
            LlmRequest request = new LlmRequest(config.getModel(), conversa, definicoes,
                    ultimo ? LlmRequest.ToolChoice.NONE : LlmRequest.ToolChoice.AUTO, config.getTemperature(),
                    config.getMaxOutputTokens(), config.isParallelToolCalls(), vazioParaNulo(config.getReasoningEffort()));
            LlmResponse resposta;
            try {
                resposta = llm.stream(request, listener, run::cancelled);
            } catch (LlmException exception) {
                if (run.cancelled()) return Outcome.cancelado(textoFinal.append(textoDoCiclo).toString(), ciclo);
                if (mensagemIniciada[0]) run.emit(Packet.of(new Packet.Placement(turno, 0), Packet.Types.SECTION_END));
                LOGGER.warn("Falha do provedor de IA no ciclo {}: {}", ciclo, exception.getMessage());
                return Outcome.falhou(textoFinal.append(textoDoCiclo).toString(), ciclo, exception.getMessage());
            }
            run.somarUso(resposta.usage(), resposta.model());
            if (mensagemIniciada[0]) run.emit(Packet.of(new Packet.Placement(turno, 0), Packet.Types.SECTION_END));
            if (run.cancelled()) return Outcome.cancelado(textoFinal.append(textoDoCiclo).toString(), ciclo);

            List<LlmToolCall> chamadas = normalizar(resposta.toolCalls(), ciclo);
            if (chamadas.isEmpty() || ultimo) {
                String texto = textoDoCiclo.isEmpty() ? resposta.content() : textoDoCiclo.toString();
                if ((texto == null || texto.isBlank()) && !tentativaExtraUsada) {
                    // Resposta vazia (ou ferramentas pedidas no ultimo ciclo): uma unica tentativa extra, sem ferramentas.
                    tentativaExtraUsada = true;
                    forcarResposta = true;
                    maxCiclos = Math.max(maxCiclos, ciclo + 1);
                    continue;
                }
                textoFinal.append(texto == null ? "" : texto);
                return Outcome.concluido(textoFinal.toString(), ciclo);
            }

            conversa.add(new LlmMessage.Assistant(resposta.content(), resposta.reasoningContent(), chamadas));
            List<ToolExecutor.Outcome> resultados = executor.executar(run, ciclo, chamadas, porNome);
            boolean algumSucesso = false;
            boolean repeticao = false;
            for (ToolExecutor.Outcome resultado : resultados) {
                conversa.add(new LlmMessage.Tool(resultado.chamada().id(), resultado.chamada().name(), resultado.conteudo()));
                if (resultado.sucesso()) algumSucesso = true;
                int vezes = assinaturas.merge(resultado.assinatura(), 1, Integer::sum);
                if (vezes > REPETICOES_MAXIMAS) repeticao = true;
            }
            falhasSeguidas = algumSucesso ? 0 : falhasSeguidas + 1;
            if (falhasSeguidas >= Math.max(1, limites.getMaxConsecutiveToolFailures())) {
                LOGGER.info("Execucao {}: {} ciclos seguidos com falha de ferramentas; forcando resposta", run.run().id(), falhasSeguidas);
                forcarResposta = true;
            }
            if (repeticao) {
                conversa.add(new LlmMessage.System("Voce repetiu a mesma chamada de ferramenta com os mesmos argumentos. "
                        + "Use os resultados que ja possui ou mude a abordagem; nao repita a chamada."));
            }
        }
        return Outcome.concluido(textoFinal.toString(), ciclo);
    }

    /** Garante ids unicos e nomes limpos nas chamadas (alguns provedores omitem o id). */
    private static List<LlmToolCall> normalizar(List<LlmToolCall> chamadas, int ciclo) {
        List<LlmToolCall> normalizadas = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int indice = 0;
        for (LlmToolCall chamada : chamadas) {
            indice++;
            if (chamada.name() == null || chamada.name().isBlank()) continue;
            String id = chamada.id();
            if (id == null || id.isBlank() || !ids.add(id)) {
                id = "call_" + ciclo + "_" + indice + "_" + UUID.randomUUID().toString().substring(0, 8);
                ids.add(id);
            }
            normalizadas.add(new LlmToolCall(id, chamada.name().trim(), chamada.arguments()));
        }
        return normalizadas;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public enum Status { CONCLUIDO, CANCELADO, FALHOU }

    public record Outcome(Status status, String texto, int ciclos, String erro) {
        static Outcome concluido(String texto, int ciclos) {
            return new Outcome(Status.CONCLUIDO, texto, ciclos, null);
        }

        static Outcome cancelado(String texto, int ciclos) {
            return new Outcome(Status.CANCELADO, texto, ciclos, null);
        }

        static Outcome falhou(String texto, int ciclos, String erro) {
            return new Outcome(Status.FALHOU, texto, ciclos, erro);
        }
    }
}
