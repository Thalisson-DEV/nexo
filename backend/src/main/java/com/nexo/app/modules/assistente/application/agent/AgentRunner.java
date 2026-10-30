package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.ConversaPersistencia;
import com.nexo.app.modules.assistente.application.artifacts.ArtifactService;
import com.nexo.app.modules.assistente.application.dto.EnviarMensagemRequest;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.llm.LlmMessage;
import com.nexo.app.modules.assistente.application.llm.LlmProvider;
import com.nexo.app.modules.assistente.application.llm.LlmResponse;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.skills.SkillCatalog;
import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;

/**
 * Orquestra uma resposta do Copiloto: prepara a pergunta (transacao curta), registra a execucao, roda o
 * loop agentic em virtual thread propria (desacoplada da conexao HTTP, como no Onyx), gera o titulo em
 * paralelo e persiste o resultado final com a timeline compactada e as metricas. Tambem atende
 * interrupcao, retomada do stream e recuperacao de respostas orfas.
 */
@Service
public class AgentRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRunner.class);

    private final AssistenteProperties properties;
    private final LlmProvider llm;
    private final AgentRunRegistry registry;
    private final ToolRegistry tools;
    private final AgentLoop loop;
    private final SystemPromptBuilder promptBuilder;
    private final ContextAssembler contextAssembler;
    private final TitleGenerator titleGenerator;
    private final SkillCatalog skills;
    private final ResultStore resultStore;
    private final ArquivoService arquivos;
    private final ArtifactService artefatos;
    private final ConversaPersistencia persistencia;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public AgentRunner(AssistenteProperties properties, LlmProvider llm, AgentRunRegistry registry, ToolRegistry tools,
            AgentLoop loop, SystemPromptBuilder promptBuilder, ContextAssembler contextAssembler, TitleGenerator titleGenerator,
            SkillCatalog skills, ResultStore resultStore, ArquivoService arquivos, ArtifactService artefatos,
            ConversaPersistencia persistencia, ObjectMapper objectMapper, ObjectProvider<MeterRegistry> meterRegistry) {
        this.properties = properties;
        this.llm = llm;
        this.registry = registry;
        this.tools = tools;
        this.loop = loop;
        this.promptBuilder = promptBuilder;
        this.contextAssembler = contextAssembler;
        this.titleGenerator = titleGenerator;
        this.skills = skills;
        this.resultStore = resultStore;
        this.arquivos = arquivos;
        this.artefatos = artefatos;
        this.persistencia = persistencia;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Inicia (ou reaproveita) a resposta a uma pergunta. Deve ser chamado na thread da requisicao: o contexto
     * de seguranca e capturado aqui e passado explicitamente ao agente.
     */
    public Inicio iniciar(UUID conversaId, SemanticQueryContext security, String nomeUsuario, EnviarMensagemRequest request) {
        return iniciar(conversaId, security, nomeUsuario, request, null);
    }

    /** Limites de uma resposta fora do chat (ex.: relatório de uma automação). Campos nulos usam a configuração. */
    public record Orcamento(Integer maxCiclos, Integer maxChamadas, Duration duracao) {
    }

    public Inicio iniciar(UUID conversaId, SemanticQueryContext security, String nomeUsuario, EnviarMensagemRequest request,
                          Orcamento orcamento) {
        if (!properties.isEnabled() || !llm.isConfigured()) {
            throw new AssistenteIndisponivelException("O Copiloto está indisponível no momento: o provedor de IA não está configurado.");
        }
        Optional<AgentRun> ativa = registry.ativaDaConversa(conversaId);
        if (ativa.isPresent()) {
            AgentRun run = ativa.get();
            if (!run.usuarioId().equals(security.usuarioId())) throw new com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException("Conversa não encontrada");
            if (Objects.equals(run.clientMessageId(), request.clientMessageId())) return Inicio.execucao(run);
            throw new com.nexo.app.shared.domain.exception.RequisicaoDuplicadaException(
                    "Já existe uma resposta em andamento nesta conversa. Aguarde ou interrompa a geração.");
        }

        ConversaPersistencia.Preparacao preparacao = persistencia.preparar(conversaId, security.usuarioId(), request);
        if (preparacao.existente() != null) {
            MensagemAssistente existente = preparacao.existente();
            return registry.daConversa(conversaId).filter(run -> run.mensagemAssistenteId().equals(existente.getId()))
                    .map(Inicio::execucao)
                    .orElseGet(() -> Inicio.concluida(existente));
        }

        AgentRun run = new AgentRun(conversaId, security.usuarioId(), preparacao.mensagemUsuarioId(),
                preparacao.mensagemAssistenteId(), request.clientMessageId(), this::serializar);
        try {
            registry.registrar(run);
        } catch (RuntimeException exception) {
            persistencia.concluir(preparacao.mensagemAssistenteId(), StatusMensagemAssistente.FALHOU,
                    "Não foi possível iniciar esta resposta: " + exception.getMessage(), "[]", "[]", null, exception.getMessage());
            throw exception;
        }
        AgentRunContext context = new AgentRunContext(run, security, nomeUsuario, preparacao.contexto(),
                preparacao.anexos().stream().map(anexo -> anexo.getId()).toList(),
                orcamento != null && orcamento.duracao() != null ? orcamento.duracao()
                        : Duration.ofSeconds(Math.max(60, properties.getAgent().getMaxRunSeconds())),
                preparacao.resultadosExistentes(), preparacao.artefatosExistentes(), preparacao.arquivosExistentes());
        if (orcamento != null) context.definirOrcamento(orcamento.maxCiclos(), orcamento.maxChamadas());
        run.emit(Packet.of(Packet.Placement.ROOT, Packet.Types.RUN_INFO,
                "conversaId", conversaId.toString(),
                "execucaoId", run.id().toString(),
                "mensagemId", preparacao.mensagemAssistenteId().toString(),
                "mensagemUsuarioId", preparacao.mensagemUsuarioId().toString(),
                "clientMessageId", request.clientMessageId().toString(),
                "anexos", preparacao.anexos().stream().map(arquivos::toResponse).toList()));
        Thread.ofVirtual().name("copiloto-" + run.id()).start(() -> executar(run, context, preparacao, request));
        return Inicio.execucao(run);
    }

    private void executar(AgentRun run, AgentRunContext context, ConversaPersistencia.Preparacao preparacao,
            EnviarMensagemRequest request) {
        long inicio = System.nanoTime();
        Thread titulo = null;
        AgentLoop.Outcome resultado = null;
        String erro = null;
        try {
            if (preparacao.primeiraPergunta()) {
                titulo = Thread.ofVirtual().name("copiloto-titulo-" + run.id()).start(() -> gerarTitulo(run, request.conteudo()));
            }
            List<AgentTool> disponiveis = tools.disponiveis(context);
            String prompt = promptBuilder.build(context, disponiveis, resultStore.listarResumos(run.conversaId()),
                    arquivos.listarDaConversa(run.conversaId()), artefatos.listar(run.conversaId()),
                    skills.disponiveis(context.security()));
            List<LlmMessage> mensagens = contextAssembler.montar(prompt, preparacao.historico(), request.conteudo(),
                    preparacao.anexos(), preparacao.contexto());
            resultado = loop.executar(context, mensagens, disponiveis);
        } catch (RuntimeException exception) {
            LOGGER.error("Execucao {} do Copiloto falhou", run.id(), exception);
            erro = "Falha inesperada ao processar a resposta.";
        }
        if (titulo != null) {
            try {
                titulo.join(Duration.ofSeconds(15));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        finalizar(run, context, resultado, erro, System.nanoTime() - inicio);
    }

    private void finalizar(AgentRun run, AgentRunContext context, AgentLoop.Outcome resultado, String erroInesperado,
            long duracaoNanos) {
        StatusMensagemAssistente status;
        String texto;
        String erro = erroInesperado;
        // Mensagem exibida ao usuario no pacote "error" (sem repetir o texto parcial ja transmitido).
        String aviso = null;
        int ciclos = resultado == null ? 0 : resultado.ciclos();
        if (resultado == null) {
            status = StatusMensagemAssistente.FALHOU;
            aviso = "Não foi possível concluir esta resposta. Tente novamente em instantes.";
            texto = aviso;
        } else {
            texto = resultado.texto();
            switch (resultado.status()) {
                case CANCELADO -> {
                    status = StatusMensagemAssistente.CANCELADA;
                    if (texto == null || texto.isBlank()) texto = "Geração interrompida pelo usuário.";
                }
                case FALHOU -> {
                    status = StatusMensagemAssistente.FALHOU;
                    erro = resultado.erro();
                    aviso = "Não foi possível concluir a resposta: o provedor de IA não respondeu como esperado. Tente novamente.";
                    texto = (texto == null || texto.isBlank() ? "" : texto.strip() + "\n\n") + "_" + aviso + "_";
                }
                default -> {
                    status = StatusMensagemAssistente.CONCLUIDA;
                    if (texto == null || texto.isBlank()) {
                        status = StatusMensagemAssistente.FALHOU;
                        aviso = "Não consegui formular uma resposta desta vez. Reformule a pergunta ou tente novamente.";
                        texto = aviso;
                        erro = "Resposta vazia do modelo";
                    }
                }
            }
        }
        if (aviso != null) {
            run.emit(Packet.of(Packet.Placement.ROOT, Packet.Types.ERROR, "mensagem", aviso));
        }
        long duracaoMs = Duration.ofNanos(duracaoNanos).toMillis();
        LlmResponse.LlmUsage uso = context.uso();
        MensagemAssistente.MetricasExecucao metricas = new MensagemAssistente.MetricasExecucao(
                Optional.ofNullable(context.modelo()).orElse(properties.getLlm().getModel()),
                uso.promptTokens(), uso.completionTokens(), custo(uso), duracaoMs, ciclos, context.chamadasFerramenta());
        try {
            // Persiste antes do pacote final: quem recarregar a conversa apos o "stop" ja encontra a resposta salva.
            persistencia.concluir(run.mensagemAssistenteId(), status, texto,
                    json(PacketCompactor.compactar(run.snapshotPackets())), json(context.navegacao().snapshot()), metricas, erro);
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao persistir a resposta {} do Copiloto", run.mensagemAssistenteId(), exception);
        } finally {
            run.emit(Packet.of(Packet.Placement.ROOT, Packet.Types.STOP,
                    "status", status.name(),
                    "mensagemId", run.mensagemAssistenteId().toString(),
                    "duracaoMs", duracaoMs,
                    "resultados", context.resultadosDoTurno(),
                    "artefatos", context.artefatosDoTurno()));
            run.finish();
        }
        registrarMetricas(status, duracaoMs, uso, context.chamadasFerramenta());
    }

    private void gerarTitulo(AgentRun run, String pergunta) {
        titleGenerator.gerar(pergunta).ifPresent(titulo -> {
            try {
                if (persistencia.aplicarTitulo(run.conversaId(), titulo)) {
                    run.emit(Packet.of(Packet.Placement.ROOT, Packet.Types.TITLE, "titulo", titulo));
                }
            } catch (RuntimeException exception) {
                LOGGER.info("Titulo da conversa {} nao aplicado: {}", run.conversaId(), exception.getClass().getSimpleName());
            }
        });
    }

    /** Solicita a interrupcao da execucao ativa da conversa (o conteudo parcial e preservado). */
    public boolean parar(UUID conversaId, UUID usuarioId) {
        return registry.ativaDaConversa(conversaId)
                .filter(run -> run.usuarioId().equals(usuarioId))
                .map(run -> {
                    run.requestCancel();
                    return true;
                })
                .orElse(false);
    }

    /** Execucao (ativa ou recem concluida) para retomar o stream a partir de um cursor. */
    public Optional<AgentRun> execucao(UUID conversaId, UUID usuarioId) {
        return registry.daConversa(conversaId).filter(run -> run.usuarioId().equals(usuarioId));
    }

    public boolean emAndamento(UUID conversaId) {
        return registry.ativaDaConversa(conversaId).isPresent();
    }

    public Optional<UUID> mensagemEmAndamento(UUID conversaId) {
        return registry.ativaDaConversa(conversaId).map(AgentRun::mensagemAssistenteId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarNaInicializacao() {
        try {
            int recuperadas = persistencia.recuperarOrfas(registry::possuiExecucaoAtiva, Duration.ZERO);
            if (recuperadas > 0) LOGGER.info("{} respostas interrompidas do Copiloto marcadas como falhas", recuperadas);
        } catch (RuntimeException exception) {
            LOGGER.warn("Nao foi possivel recuperar respostas orfas: {}", exception.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void recuperarPeriodicamente() {
        try {
            Duration idade = Duration.ofSeconds(Math.max(120, properties.getAgent().getMaxRunSeconds() + 120L));
            persistencia.recuperarOrfas(registry::possuiExecucaoAtiva, idade);
        } catch (RuntimeException exception) {
            LOGGER.debug("Recuperacao periodica falhou: {}", exception.getClass().getSimpleName());
        }
    }

    public String serializar(Packet packet) {
        try {
            return objectMapper.writeValueAsString(packet);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Pacote {} nao serializavel: {}", packet.type(), exception.getOriginalMessage());
            return "{\"placement\":{\"turnIndex\":" + packet.placement().turnIndex() + ",\"tabIndex\":"
                    + packet.placement().tabIndex() + "},\"obj\":{\"type\":\"" + packet.type() + "\"}}";
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private BigDecimal custo(LlmResponse.LlmUsage uso) {
        AssistenteProperties.Llm config = properties.getLlm();
        BigDecimal milhao = BigDecimal.valueOf(1_000_000);
        BigDecimal entrada = config.getInputCostPerMillionTokens() == null ? BigDecimal.ZERO : config.getInputCostPerMillionTokens();
        BigDecimal saida = config.getOutputCostPerMillionTokens() == null ? BigDecimal.ZERO : config.getOutputCostPerMillionTokens();
        return entrada.multiply(BigDecimal.valueOf(uso.promptTokens())).divide(milhao, 6, RoundingMode.HALF_UP)
                .add(saida.multiply(BigDecimal.valueOf(uso.completionTokens())).divide(milhao, 6, RoundingMode.HALF_UP));
    }

    private void registrarMetricas(StatusMensagemAssistente status, long duracaoMs, LlmResponse.LlmUsage uso, int ferramentas) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) return;
        Timer.builder("copiloto.execucao.duracao").tag("status", status.name()).register(registry)
                .record(Duration.ofMillis(duracaoMs));
        Counter.builder("copiloto.tokens").tag("tipo", "entrada").register(registry).increment(uso.promptTokens());
        Counter.builder("copiloto.tokens").tag("tipo", "saida").register(registry).increment(uso.completionTokens());
        Counter.builder("copiloto.ferramentas.chamadas").register(registry).increment(ferramentas);
    }

    /** Desfecho do pedido de inicio: execucao para transmitir ou resposta ja concluida (pergunta repetida). */
    public record Inicio(AgentRun execucao, MensagemAssistente concluida) {
        static Inicio execucao(AgentRun run) {
            return new Inicio(run, null);
        }

        static Inicio concluida(MensagemAssistente mensagem) {
            return new Inicio(null, mensagem);
        }
    }
}
