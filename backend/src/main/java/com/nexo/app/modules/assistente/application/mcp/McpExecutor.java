package com.nexo.app.modules.assistente.application.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.AgentRun;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.Packet;
import com.nexo.app.modules.assistente.application.agent.ToolContext;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.agent.ToolResult;
import com.nexo.app.modules.assistente.application.conexoes.ConexaoIaService;
import com.nexo.app.modules.assistente.domain.ConexaoIa;
import com.nexo.app.modules.assistente.domain.ExecucaoFerramentaAssistente;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArquivoAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArtefatoAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ExecucaoFerramentaAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ResultadoAssistenteRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Executa uma ferramenta do Copiloto pedida por uma IA externa. Mesmo contrato do chat (permissao no
 * backend, timeout, resultado truncado, auditoria em {@code assistente_execucao_ferramenta}), sem a
 * confirmacao na tela: no MCP quem confirma a escrita e o aplicativo de IA, e a conexao so recebe
 * ferramentas de escrita quando a pessoa permitiu.
 *
 * <p>As chamadas de uma mesma conexao rodam uma de cada vez: os codigos de resultado (r1, r2...)
 * seguem a sequencia da conversa tecnica da conexao e outras ferramentas podem reutiliza-los.</p>
 */
@Component
public class McpExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpExecutor.class);
    private static final int MAX_ARGUMENTOS_AUDITORIA = 20_000;
    private static final Duration ESPERA_MAXIMA_NA_FILA = Duration.ofSeconds(90);

    private final ConexaoIaService conexoes;
    private final ResultadoAssistenteRepository resultados;
    private final ArtefatoAssistenteRepository artefatos;
    private final ArquivoAssistenteRepository arquivos;
    private final ExecucaoFerramentaAssistenteRepository auditoria;
    private final AssistenteProperties properties;
    private final ObjectProvider<MeterRegistry> metricas;
    private final Map<UUID, ReentrantLock> filas = new ConcurrentHashMap<>();

    public McpExecutor(ConexaoIaService conexoes, ResultadoAssistenteRepository resultados,
            ArtefatoAssistenteRepository artefatos, ArquivoAssistenteRepository arquivos,
            ExecucaoFerramentaAssistenteRepository auditoria, AssistenteProperties properties,
            ObjectProvider<MeterRegistry> metricas) {
        this.conexoes = conexoes;
        this.resultados = resultados;
        this.artefatos = artefatos;
        this.arquivos = arquivos;
        this.auditoria = auditoria;
        this.properties = properties;
        this.metricas = metricas;
    }

    /** Resultado da chamada: texto devolvido a IA e se a ferramenta falhou. */
    public record Resposta(String texto, boolean erro) {
    }

    public Resposta executar(AgentTool tool, JsonNode argumentos, SessaoMcp sessao) {
        UUID conexaoId = sessao.acesso().conexaoId();
        ReentrantLock fila = filas.computeIfAbsent(conexaoId, ignored -> new ReentrantLock(true));
        try {
            if (!fila.tryLock(ESPERA_MAXIMA_NA_FILA.toSeconds(), TimeUnit.SECONDS)) {
                return new Resposta("O Nexo ainda esta processando outra chamada desta conexao. Tente de novo em instantes.", true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resposta("Chamada interrompida.", true);
        }
        try {
            return executarNaFila(tool, argumentos, sessao, conexaoId);
        } finally {
            fila.unlock();
        }
    }

    private Resposta executarNaFila(AgentTool tool, JsonNode argumentos, SessaoMcp sessao, UUID conexaoId) {
        long inicio = System.nanoTime();
        ConexaoIa conexao = conexoes.garantirConversa(conexaoId);
        UUID conversa = conexao.getConversaId();
        AgentRun run = new AgentRun(conversa, sessao.seguranca().usuarioId(), null, conexao.getMensagemId(), null,
                pacote -> "");
        AgentRunContext contexto = new AgentRunContext(run, sessao.seguranca(), sessao.nomeUsuario(), null, List.of(),
                tool.timeout().plusSeconds(30), (int) resultados.countByConversaId(conversa),
                (int) artefatos.countByConversaId(conversa), (int) arquivos.contarCodificados(conversa));
        String chamadaId = "mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        ToolContext toolContext = new ToolContext(contexto, chamadaId, new Packet.Placement(1, 1), 1);

        ToolResult resultado = null;
        String erro = null;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // O usuario da conexao segue para a thread da ferramenta: auditoria (usuario_criacao) e regras
            // que leem o contexto de seguranca enxergam a pessoa, nao SYSTEM.
            Future<ToolResult> futuro = executor.submit(new DelegatingSecurityContextCallable<>(
                    () -> tool.execute(argumentos, toolContext)));
            try {
                resultado = futuro.get(tool.timeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                futuro.cancel(true);
                erro = "A ferramenta excedeu o tempo limite de " + tool.timeout().toSeconds() + " s. Simplifique o pedido "
                        + "(periodo menor, agregacao, menos colunas).";
            } catch (ExecutionException exception) {
                Throwable causa = exception.getCause();
                if (causa instanceof ToolException toolException) {
                    erro = toolException.getMessage();
                } else {
                    LOGGER.error("Erro inesperado na ferramenta {} chamada pelo MCP", tool.name(), causa);
                    erro = "Erro interno ao executar a ferramenta. Tente outra abordagem.";
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                futuro.cancel(true);
                erro = "Chamada interrompida.";
            }
        } finally {
            run.finish();
        }
        if (resultado == null && erro == null) erro = "A ferramenta nao retornou resultado.";

        long duracaoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        boolean sucesso = erro == null && !resultado.failed();
        auditar(conexao, sessao, tool, argumentos, chamadaId, sucesso, duracaoMs, resultado, erro);
        conexoes.registrarChamada(conexaoId);
        registrarMetrica(tool.name(), sucesso, duracaoMs);
        if (erro != null) return new Resposta(erro, true);
        int limite = tool.maxResultChars() > 0 ? tool.maxResultChars() : properties.getAgent().getToolResultMaxChars();
        return new Resposta(truncar(Objects.toString(resultado.llmContent(), ""), limite), resultado.failed());
    }

    private void auditar(ConexaoIa conexao, SessaoMcp sessao, AgentTool tool, JsonNode argumentos, String chamadaId,
            boolean sucesso, long duracaoMs, ToolResult resultado, String erro) {
        try {
            JsonNode exibicao = tool.displayArguments(argumentos);
            String texto = exibicao == null ? null : exibicao.toString();
            if (texto != null && texto.length() > MAX_ARGUMENTOS_AUDITORIA) texto = texto.substring(0, MAX_ARGUMENTOS_AUDITORIA);
            auditoria.save(new ExecucaoFerramentaAssistente(conexao.getMensagemId(), conexao.getConversaId(),
                    sessao.seguranca().usuarioId(), tool.name(), chamadaId, 0, tool.riskLevel().name(), texto, sucesso,
                    duracaoMs, resultado == null ? null : resultado.summary(), erro,
                    resultado == null ? null : resultado.resultCode(), resultado == null ? null : resultado.rowCount()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Falha ao auditar chamada MCP da ferramenta {}: {}", tool.name(), exception.getClass().getSimpleName());
        }
    }

    private void registrarMetrica(String nome, boolean sucesso, long duracaoMs) {
        MeterRegistry registry = metricas.getIfAvailable();
        if (registry == null) return;
        Timer.builder("ces.mcp.ferramenta.duracao")
                .tag("ferramenta", nome)
                .tag("status", sucesso ? "ok" : "erro")
                .register(registry)
                .record(Duration.ofMillis(duracaoMs));
    }

    static String truncar(String conteudo, int limite) {
        if (conteudo.length() <= limite) return conteudo;
        return conteudo.substring(0, limite) + "\n...[conteudo truncado: " + (conteudo.length() - limite)
                + " caracteres omitidos. Refine a consulta ou use o codigo do resultado nas ferramentas de analise.]";
    }
}
