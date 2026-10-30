package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.llm.LlmToolCall;
import com.nexo.app.modules.assistente.domain.AcaoPendenteAssistente;
import com.nexo.app.modules.assistente.domain.ExecucaoFerramentaAssistente;
import com.nexo.app.modules.assistente.infrastructure.persistence.AcaoPendenteAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ExecucaoFerramentaAssistenteRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executa as chamadas de ferramenta de um ciclo em paralelo (virtual threads), como o
 * run_tool_calls do Onyx: valida nome, argumentos e autorizacao; aplica timeout por ferramenta; trunca o
 * retorno ao orcamento de contexto; publica tool_start/tool_result na timeline; audita cada execucao e
 * registra metricas. Ferramentas com risco de escrita nunca executam direto: viram acao pendente.
 */
@Component
public class ToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);
    private static final int MAX_ARGUMENTOS_AUDITORIA = 20_000;
    private static final int MAX_PARALELO = 8;

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final AssistenteProperties properties;
    private final ExecucaoFerramentaAssistenteRepository auditoria;
    private final AcaoPendenteAssistenteRepository acoesPendentes;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public ToolExecutor(ToolRegistry registry, ObjectMapper objectMapper, AssistenteProperties properties,
            ExecucaoFerramentaAssistenteRepository auditoria, AcaoPendenteAssistenteRepository acoesPendentes,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.auditoria = auditoria;
        this.acoesPendentes = acoesPendentes;
        this.meterRegistry = meterRegistry;
    }

    public List<Outcome> executar(AgentRunContext run, int ciclo, List<LlmToolCall> chamadas, Map<String, AgentTool> oferecidas) {
        List<Outcome> resultados = new ArrayList<>(Collections.nCopies(chamadas.size(), null));
        Semaphore limite = new Semaphore(MAX_PARALELO);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Outcome>> futuros = new ArrayList<>();
            for (int i = 0; i < chamadas.size(); i++) {
                LlmToolCall chamada = chamadas.get(i);
                Packet.Placement placement = new Packet.Placement(ciclo, i + 1);
                futuros.add(executor.submit(() -> {
                    limite.acquire();
                    try {
                        return executarUma(run, ciclo, chamada, placement, oferecidas);
                    } finally {
                        limite.release();
                    }
                }));
            }
            for (int i = 0; i < futuros.size(); i++) {
                try {
                    resultados.set(i, futuros.get(i).get());
                } catch (ExecutionException exception) {
                    LOGGER.error("Falha inesperada no executor de ferramentas", exception.getCause());
                    resultados.set(i, Outcome.falha(chamadas.get(i), "Erro interno ao executar a ferramenta."));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    resultados.set(i, Outcome.falha(chamadas.get(i), "Execucao interrompida."));
                }
            }
        }
        return resultados;
    }

    private Outcome executarUma(AgentRunContext run, int ciclo, LlmToolCall chamada, Packet.Placement placement,
            Map<String, AgentTool> oferecidas) {
        long inicio = System.nanoTime();
        ToolContext context = new ToolContext(run, chamada.id(), placement, ciclo);
        String nome = chamada.name() == null ? "" : chamada.name().trim();
        AgentTool tool = oferecidas.get(nome);
        if (tool == null) {
            String mensagem = registry.buscar(nome).isPresent()
                    ? "A ferramenta '" + nome + "' nao esta disponivel nesta conversa. Use somente as ferramentas oferecidas."
                    : "Ferramenta inexistente: '" + nome + "'. Use somente as ferramentas oferecidas.";
            context.emit(Packet.Types.TOOL_START, "ferramenta", nome, "titulo", nome, "tipo", "CATALOG",
                    "rotulo", "Ferramenta indisponível");
            return concluir(run, context, null, nome, JsonNodeFactory.instance.objectNode(), null, mensagem, inicio, ciclo, chamada);
        }
        run.registrarChamadaFerramenta();
        JsonNode argumentos;
        try {
            argumentos = interpretarArgumentos(chamada.arguments());
        } catch (JsonProcessingException exception) {
            context.emit(Packet.Types.TOOL_START, "ferramenta", tool.name(), "titulo", tool.displayName(),
                    "tipo", tool.kind().name(), "rotulo", tool.displayName());
            return concluir(run, context, tool, nome, JsonNodeFactory.instance.objectNode(), null,
                    "Argumentos JSON invalidos para " + nome + ". Envie um objeto JSON valido conforme o schema.", inicio, ciclo, chamada);
        }
        JsonNode exibicao = exibir(tool, argumentos);
        context.emit(Packet.Types.TOOL_START, "ferramenta", tool.name(), "titulo", tool.displayName(),
                "tipo", tool.kind().name(), "rotulo", rotulo(tool, argumentos), "argumentos", exibicao);

        if (!registry.autorizada(tool, run.security())) {
            return concluir(run, context, tool, nome, exibicao, null,
                    "O usuario nao tem permissao para esta ferramenta. Explique a limitacao sem detalhes internos.", inicio, ciclo, chamada);
        }
        if (tool.riskLevel().requiresConfirmation()) {
            return proporAcao(run, context, tool, argumentos, exibicao, inicio, ciclo, chamada);
        }

        Duration timeout = tool.timeout();
        ToolResult resultado;
        String erro = null;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ToolResult> futuro = executor.submit(() -> tool.execute(argumentos, context));
            try {
                resultado = futuro.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                futuro.cancel(true);
                resultado = null;
                erro = "A ferramenta excedeu o tempo limite de " + timeout.toSeconds() + " s. Simplifique a solicitacao "
                        + "(periodo menor, agregacao no banco, menos colunas).";
            } catch (ExecutionException exception) {
                resultado = null;
                Throwable causa = exception.getCause();
                if (causa instanceof ToolException toolException) {
                    erro = toolException.getMessage();
                } else {
                    LOGGER.error("Erro inesperado na ferramenta {}", tool.name(), causa);
                    erro = "Erro interno ao executar a ferramenta. Tente outra abordagem ou outra ferramenta.";
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                futuro.cancel(true);
                resultado = null;
                erro = "Execucao interrompida.";
            }
        }
        if (resultado == null && erro == null) erro = "A ferramenta nao retornou resultado.";
        return concluir(run, context, tool, nome, exibicao, resultado, erro, inicio, ciclo, chamada);
    }

    private Outcome proporAcao(AgentRunContext run, ToolContext context, AgentTool tool, JsonNode argumentos,
            JsonNode exibicao, long inicio, int ciclo, LlmToolCall chamada) {
        if (tool instanceof AcaoComPrevia comPrevia) {
            return proporComPrevia(run, context, tool, comPrevia, argumentos, exibicao, inicio, ciclo, chamada);
        }
        String argumentosJson = argumentos.toString();
        AcaoPendenteAssistente acao = acoesPendentes.save(new AcaoPendenteAssistente(run.conversaId(),
                run.mensagemAssistenteId(), run.usuarioId(), tool.name(), argumentosJson, exibicao.toString(),
                OffsetDateTime.now().plusMinutes(30)));
        context.emit(Packet.Types.ACTION_PROPOSAL, "acaoId", acao.getId().toString(), "ferramenta", tool.name(),
                "titulo", tool.displayName(), "previa", exibicao, "risco", tool.riskLevel().name());
        ToolResult resultado = ToolResult.of("{\"status\":\"AGUARDANDO_CONFIRMACAO\",\"orientacao\":\"A acao foi apresentada "
                + "ao usuario para confirmacao. Nada foi executado. Explique a previa e aguarde.\"}", "Aguardando confirmação");
        return concluir(run, context, tool, tool.name(), exibicao, resultado, null, inicio, ciclo, chamada);
    }

    /** A ferramenta monta a previa (nomes resolvidos, pendencias apontadas); a anterior equivalente expira. */
    private Outcome proporComPrevia(AgentRunContext run, ToolContext context, AgentTool tool, AcaoComPrevia comPrevia,
            JsonNode argumentos, JsonNode exibicao, long inicio, int ciclo, LlmToolCall chamada) {
        AcaoComPrevia.PreviaAcao previa;
        try {
            previa = comPrevia.preparar(argumentos, context.security());
        } catch (ToolException exception) {
            return concluir(run, context, tool, tool.name(), exibicao, null, exception.getMessage(), inicio, ciclo, chamada);
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao preparar a previa da ferramenta {}", tool.name(), exception);
            return concluir(run, context, tool, tool.name(), exibicao, null,
                    "Erro interno ao preparar a acao. Tente de novo ou peca ao usuario para usar a tela do modulo.", inicio, ciclo, chamada);
        }
        if (previa.chave() != null) {
            for (AcaoPendenteAssistente anterior : acoesPendentes.findByConversaIdAndStatus(run.conversaId(),
                    AcaoPendenteAssistente.Status.PENDENTE)) {
                if (anterior.getFerramenta().equals(tool.name()) && previa.chave().equals(chaveDaPrevia(anterior))) {
                    anterior.expirar();
                    acoesPendentes.save(anterior);
                }
            }
        }
        AcaoPendenteAssistente acao = acoesPendentes.save(new AcaoPendenteAssistente(run.conversaId(),
                run.mensagemAssistenteId(), run.usuarioId(), tool.name(), previa.argumentos().toString(),
                previa.exibicao().toString(), OffsetDateTime.now().plusHours(12)));
        context.emit(Packet.Types.ACTION_PROPOSAL, "acaoId", acao.getId().toString(), "ferramenta", tool.name(),
                "titulo", tool.displayName(), "previa", previa.exibicao(), "risco", tool.riskLevel().name());
        ToolResult resultado = ToolResult.of(previa.paraModelo(), previa.resumo());
        return concluir(run, context, tool, tool.name(), exibicao, resultado, null, inicio, ciclo, chamada);
    }

    private String chaveDaPrevia(AcaoPendenteAssistente acao) {
        try {
            JsonNode previa = objectMapper.readTree(acao.getPreviaJson());
            return previa.path("chave").asText(null);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Outcome concluir(AgentRunContext run, ToolContext context, AgentTool tool, String nome, JsonNode exibicao,
            ToolResult resultado, String erro, long inicio, int ciclo, LlmToolCall chamada) {
        long duracaoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        boolean sucesso = erro == null && resultado != null && !resultado.failed();
        String conteudo;
        if (erro != null) {
            conteudo = "{\"erro\":" + jsonString(erro) + "}";
        } else {
            int limite = tool != null && tool.maxResultChars() > 0 ? tool.maxResultChars()
                    : properties.getAgent().getToolResultMaxChars();
            conteudo = truncar(Objects.toString(resultado.llmContent(), ""), limite);
        }
        Map<String, Object> pacote = new LinkedHashMap<>();
        pacote.put("status", sucesso ? "ok" : "erro");
        pacote.put("duracaoMs", duracaoMs);
        if (erro != null) {
            pacote.put("resumo", "Não foi possível concluir");
            pacote.put("erro", erro);
        } else {
            pacote.put("resumo", resultado.summary());
            if (resultado.resultCode() != null) pacote.put("resultado", resultado.resultCode());
            if (resultado.rowCount() != null) pacote.put("linhas", resultado.rowCount());
            if (resultado.preview() != null) pacote.put("previa", resultado.preview());
            if (!resultado.sources().isEmpty()) pacote.put("fontes", resultado.sources());
            if (!resultado.files().isEmpty()) pacote.put("arquivos", resultado.files());
        }
        List<Object> pares = new ArrayList<>();
        pacote.forEach((chave, valor) -> {
            pares.add(chave);
            pares.add(valor);
        });
        context.emit(Packet.Types.TOOL_RESULT, pares.toArray());
        auditar(run, tool, nome, exibicao, sucesso, duracaoMs, resultado, erro, ciclo, chamada);
        registrarMetrica(nome, sucesso, duracaoMs);
        return new Outcome(chamada, conteudo, sucesso, erro != null, assinatura(nome, exibicao));
    }

    private void auditar(AgentRunContext run, AgentTool tool, String nome, JsonNode exibicao, boolean sucesso, long duracaoMs,
            ToolResult resultado, String erro, int ciclo, LlmToolCall chamada) {
        try {
            String argumentos = exibicao == null ? null : exibicao.toString();
            if (argumentos != null && argumentos.length() > MAX_ARGUMENTOS_AUDITORIA) {
                argumentos = argumentos.substring(0, MAX_ARGUMENTOS_AUDITORIA);
            }
            auditoria.save(new ExecucaoFerramentaAssistente(run.mensagemAssistenteId(), run.conversaId(), run.usuarioId(),
                    nome, chamada.id(), ciclo, tool == null ? null : tool.riskLevel().name(), argumentos, sucesso, duracaoMs,
                    resultado == null ? null : resultado.summary(), erro,
                    resultado == null ? null : resultado.resultCode(), resultado == null ? null : resultado.rowCount()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Falha ao auditar execucao da ferramenta {}: {}", nome, exception.getClass().getSimpleName());
        }
    }

    private void registrarMetrica(String nome, boolean sucesso, long duracaoMs) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) return;
        Timer.builder("copiloto.ferramenta.duracao")
                .tag("ferramenta", nome.isBlank() ? "desconhecida" : nome)
                .tag("status", sucesso ? "ok" : "erro")
                .register(registry)
                .record(Duration.ofMillis(duracaoMs));
    }

    private JsonNode interpretarArgumentos(String bruto) throws JsonProcessingException {
        if (bruto == null || bruto.isBlank()) return JsonNodeFactory.instance.objectNode();
        String texto = bruto.strip();
        try {
            JsonNode node = objectMapper.readTree(texto);
            if (node != null && node.isTextual()) node = objectMapper.readTree(node.asText());
            if (node == null || !node.isObject()) throw new JsonProcessingException("Argumentos devem ser um objeto") { };
            return node;
        } catch (JsonProcessingException exception) {
            // Modelos ocasionalmente anexam texto apos o JSON: aproveita o primeiro objeto completo.
            int inicio = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (inicio >= 0 && fim > inicio) {
                JsonNode node = objectMapper.readTree(texto.substring(inicio, fim + 1));
                if (node.isObject()) return node;
            }
            throw exception;
        }
    }

    private static JsonNode exibir(AgentTool tool, JsonNode argumentos) {
        try {
            JsonNode exibicao = tool.displayArguments(argumentos);
            return exibicao == null ? JsonNodeFactory.instance.objectNode() : exibicao;
        } catch (RuntimeException exception) {
            return argumentos;
        }
    }

    private static String rotulo(AgentTool tool, JsonNode argumentos) {
        try {
            String rotulo = tool.progressLabel(argumentos);
            return rotulo == null || rotulo.isBlank() ? tool.displayName() : rotulo;
        } catch (RuntimeException exception) {
            return tool.displayName();
        }
    }

    private String jsonString(String valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException exception) {
            return "\"erro\"";
        }
    }

    static String truncar(String conteudo, int limite) {
        if (conteudo.length() <= limite) return conteudo;
        return conteudo.substring(0, limite) + "\n...[conteudo truncado: " + (conteudo.length() - limite)
                + " caracteres omitidos. Use o codigo do resultado para analisar o restante.]";
    }

    private static String assinatura(String nome, JsonNode argumentos) {
        return nome + ":" + (argumentos == null ? "" : argumentos.toString());
    }

    /**
     * Desfecho de uma chamada: conteudo devolvido ao modelo, sucesso e assinatura (nome + argumentos)
     * usada pelo guarda de repeticao do loop.
     */
    public record Outcome(LlmToolCall chamada, String conteudo, boolean sucesso, boolean erroDeExecucao, String assinatura) {
        static Outcome falha(LlmToolCall chamada, String mensagem) {
            return new Outcome(chamada, "{\"erro\":\"" + mensagem.replace("\"", "'") + "\"}", false, true,
                    chamada.name() + ":" + chamada.arguments());
        }
    }
}
