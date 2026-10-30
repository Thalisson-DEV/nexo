package com.nexo.app.modules.assistente.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.ContextoCes;
import com.nexo.app.modules.assistente.application.dto.EnviarMensagemRequest;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.domain.*;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArquivoAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArtefatoAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConversaAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.MensagemAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ResultadoAssistenteRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * Transacoes curtas do ciclo de uma resposta: preparar (pergunta, anexos, placeholder da resposta),
 * concluir (texto final, timeline e metricas), titulo e recuperacao de respostas orfas. Nenhuma transacao
 * fica aberta durante a execucao do agente.
 */
@Service
public class ConversaPersistencia {

    private final ConversaAssistenteRepository conversas;
    private final MensagemAssistenteRepository mensagens;
    private final ArquivoAssistenteRepository arquivosRepository;
    private final ResultadoAssistenteRepository resultados;
    private final ArtefatoAssistenteRepository artefatos;
    private final ArquivoService arquivos;
    private final ObjectMapper objectMapper;
    private final AssistenteProperties properties;

    public ConversaPersistencia(ConversaAssistenteRepository conversas, MensagemAssistenteRepository mensagens,
            ArquivoAssistenteRepository arquivosRepository, ResultadoAssistenteRepository resultados,
            ArtefatoAssistenteRepository artefatos, ArquivoService arquivos, ObjectMapper objectMapper,
            AssistenteProperties properties) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.arquivosRepository = arquivosRepository;
        this.resultados = resultados;
        this.artefatos = artefatos;
        this.arquivos = arquivos;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public ConversaAssistente criar(UUID usuarioId, String titulo, ContextoCes contexto) {
        ConversaAssistente conversa = new ConversaAssistente(usuarioId, titulo, contextoJson(contexto));
        if (contexto != null && contexto.dePainel()) conversa.associarPainel(contexto.painel().id());
        return conversas.saveAndFlush(conversa);
    }

    /**
     * Registra a pergunta e o placeholder da resposta. Reenvio com o mesmo {@code clientMessageId} devolve a
     * resposta existente em vez de gerar outra (idempotencia).
     */
    @Transactional
    public Preparacao preparar(UUID conversaId, UUID usuarioId, EnviarMensagemRequest request) {
        ConversaAssistente conversa = conversas.findOwnedForUpdate(conversaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa não encontrada"));
        Optional<MensagemAssistente> existente = mensagens.findByConversaIdAndClientMessageIdAndPapel(
                conversaId, request.clientMessageId(), PapelMensagem.ASSISTENTE);
        if (existente.isPresent()) {
            return new Preparacao(conversaId, null, existente.get().getId(), existente.get(), List.of(), List.of(),
                    contexto(conversa), false, 0, 0, 0);
        }
        long anteriores = mensagens.countByConversaIdAndPapelAndAtivoTrue(conversaId, PapelMensagem.USUARIO);
        List<MensagemAssistente> historico = new ArrayList<>(mensagens.findByConversaIdAndAtivoTrueOrderByDataCriacaoDesc(
                conversaId, PageRequest.of(0, Math.max(2, properties.getMaxHistoryMessages()) + 2)));
        Collections.reverse(historico);

        boolean comContexto = request.contexto() != null && !request.contexto().vazio();
        // Snapshot do contexto da tela nesta pergunta (so quando enviado): o historico continua correto
        // mesmo que o usuario troque filtros entre uma pergunta e a seguinte.
        MensagemAssistente pergunta = mensagens.saveAndFlush(MensagemAssistente.pergunta(conversa,
                request.clientMessageId(), request.conteudo(), request.arquivos().isEmpty() ? null : json(request.arquivos()),
                comContexto ? contextoJson(request.contexto()) : null));
        List<ArquivoAssistente> anexos = arquivos.vincular(conversaId, pergunta.getId(), usuarioId, request.arquivos());
        conversa.definirTituloPelaPrimeiraPergunta(request.conteudo());
        if (conversa.getContextoJson() == null && comContexto) {
            conversa.definirContexto(contextoJson(request.contexto()));
        }
        if (comContexto && request.contexto().dePainel()) conversa.associarPainel(request.contexto().painel().id());
        conversa.registrarAtividade();
        conversas.save(conversa);
        MensagemAssistente resposta = mensagens.saveAndFlush(MensagemAssistente.processando(conversa, request.clientMessageId()));

        // Sem contexto nesta pergunta (ex.: conversa de painel continuada no workspace), o painel salvo deixa de
        // ser "o que o usuario esta vendo agora": fica so a pista de origem; os recortes antigos seguem no historico.
        ContextoCes contexto = comContexto ? request.contexto() : semPainel(contexto(conversa));
        return new Preparacao(conversaId, pergunta.getId(), resposta.getId(), null, historico, anexos, contexto,
                anteriores == 0 && !conversa.isTituloDefinidoPeloUsuario(),
                Math.toIntExact(resultados.countByConversaId(conversaId)),
                Math.toIntExact(artefatos.countByConversaId(conversaId)),
                Math.toIntExact(arquivosRepository.contarCodificados(conversaId)));
    }

    @Transactional
    public void concluir(UUID mensagemId, StatusMensagemAssistente status, String texto, String pacotesJson, String acoesJson,
            MensagemAssistente.MetricasExecucao metricas, String erro) {
        MensagemAssistente mensagem = mensagens.findById(mensagemId)
                .orElseThrow(() -> new IllegalStateException("Mensagem da execucao nao encontrada"));
        if (!mensagem.isProcessando()) return;
        mensagem.concluirExecucao(status, texto, pacotesJson, acoesJson, metricas, erro);
        mensagens.save(mensagem);
        conversas.findById(mensagem.getConversa().getId()).ifPresent(conversa -> {
            conversa.registrarAtividade();
            conversas.save(conversa);
        });
    }

    @Transactional
    public boolean aplicarTitulo(UUID conversaId, String titulo) {
        return conversas.findById(conversaId).map(conversa -> {
            boolean aplicado = conversa.definirTituloGerado(titulo);
            if (aplicado) conversas.save(conversa);
            return aplicado;
        }).orElse(false);
    }

    /** Marca como falhas as respostas que ficaram PROCESSANDO sem execucao ativa (ex.: reinicio do servidor). */
    @Transactional
    public int recuperarOrfas(Predicate<UUID> execucaoAtiva, Duration idadeMinima) {
        OffsetDateTime limite = OffsetDateTime.now().minus(idadeMinima);
        int recuperadas = 0;
        for (MensagemAssistente mensagem : mensagens.findByStatus(StatusMensagemAssistente.PROCESSANDO)) {
            if (execucaoAtiva.test(mensagem.getId())) continue;
            if (mensagem.getDataCriacao() != null && mensagem.getDataCriacao().isAfter(limite)) continue;
            mensagem.concluirExecucao(StatusMensagemAssistente.FALHOU,
                    "A geração desta resposta foi interrompida antes de terminar. Envie a pergunta novamente.",
                    mensagem.getPacotesJson(), "[]", null, "Execucao interrompida (reinicio ou falha do servidor)");
            mensagens.save(mensagem);
            recuperadas++;
        }
        return recuperadas;
    }

    public ContextoCes contexto(ConversaAssistente conversa) {
        return contextoDe(conversa.getContextoJson());
    }

    /** Le um contexto persistido (conversa ou snapshot de mensagem); JSON invalido vira ausencia de contexto. */
    public ContextoCes contextoDe(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ContextoCes.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    static ContextoCes semPainel(ContextoCes contexto) {
        if (contexto == null || contexto.painel() == null) return contexto;
        ContextoCes origem = new ContextoCes(contexto.pagina(), contexto.rota(), contexto.entidadeTipo(), contexto.entidadeId(),
                contexto.rotulo(), contexto.filtros(), null);
        return origem.vazio() ? null : origem;
    }

    private String contextoJson(ContextoCes contexto) {
        if (contexto == null || contexto.vazio()) return null;
        return json(contexto);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar", exception);
        }
    }

    /**
     * Resultado da preparacao. {@code existente} preenchido indica pergunta repetida (nenhuma nova execucao).
     */
    public record Preparacao(UUID conversaId, UUID mensagemUsuarioId, UUID mensagemAssistenteId, MensagemAssistente existente,
            List<MensagemAssistente> historico, List<ArquivoAssistente> anexos, ContextoCes contexto, boolean primeiraPergunta,
            int resultadosExistentes, int artefatosExistentes, int arquivosExistentes) {
    }
}
