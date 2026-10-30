package com.nexo.app.modules.chamado.application.usecases;

import com.nexo.app.modules.chamado.application.dto.AbrirChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.AnexoChamadoDownload;
import com.nexo.app.modules.chamado.application.dto.AnexoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.AtualizarAtendimentoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.ChamadoFilter;
import com.nexo.app.modules.chamado.application.dto.ChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.ReabrirChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoResponse;
import com.nexo.app.modules.chamado.application.mappers.ChamadoMapper;
import com.nexo.app.modules.chamado.domain.AnexoChamado;
import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.modules.chamado.domain.TopicoChamado;
import com.nexo.app.modules.chamado.domain.InteracaoChamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoSpecification;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.mencao.ContextoMencao;
import com.nexo.app.modules.mencao.MencaoApi;
import com.nexo.app.modules.mencao.domain.OrigemMencao;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.ChamadoAbertoEvent;
import com.nexo.app.shared.domain.events.ChamadoAtribuidoEvent;
import com.nexo.app.shared.domain.events.ChamadoMensagemRecebidaEvent;
import com.nexo.app.shared.domain.events.ChamadoReabertoEvent;
import com.nexo.app.shared.domain.events.ChamadoResolvidoEvent;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChamadoUseCases {

    private static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;
    private static final String PERMISSAO_GERENCIAR = "chamado:manage";
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("text/plain", "txt"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
    );

    /** O bastante para reconhecer o assunto sem abrir o chamado. */
    private static final int LIMITE_TRECHO = 200;

    private final ChamadoRepository repository;
    private final TopicoChamadoUseCases topicoUseCases;
    private final EquipeAtendimentoUseCases equipeUseCases;
    private final ChamadoMapper mapper;
    private final ObjectStorageService objectStorage;
    private final UsuarioApi usuarioApi;
    private final MencaoApi mencaoApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChamadoResponse abrir(AbrirChamadoRequest request, UUID usuarioSolicitanteId) {
        exigirUsuarioAutenticado(usuarioSolicitanteId);
        TopicoChamado topico = topicoUseCases.buscarEntity(request.topicoId());
        Chamado chamado = new Chamado(topico, request.assunto(), request.descricao(),
                request.prioridade(), usuarioSolicitanteId);
        repository.save(chamado);

        // A fila só é útil se alguém souber que ela cresceu. O SLA de primeira resposta
        // começa a correr aqui, então o aviso à equipe também precisa começar aqui.
        EquipeAtendimento equipe = chamado.getEquipeAtendimento();
        eventPublisher.publishEvent(new ChamadoAbertoEvent(
                chamado.getId(),
                chamado.getProtocolo(),
                chamado.getAssunto(),
                topico.getNome(),
                chamado.getPrioridade().name(),
                equipe.getNome(),
                usuarioSolicitanteId,
                equipe.getMembros()));

        return mapper.toResponse(chamado);
    }

    @Transactional(readOnly = true)
    public ChamadoResponse buscarPorId(UUID id, UUID usuarioAutenticado) {
        return mapper.toResponse(buscarComAcesso(id, usuarioAutenticado));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChamadoResponse> listarMeus(UUID usuarioAutenticado,
                                                   ChamadoFilter filtro, Pageable pageable) {
        exigirUsuarioAutenticado(usuarioAutenticado);
        return PageResponse.fromPage(repository
                .findAll(ChamadoSpecification.comFiltros(filtro, usuarioAutenticado), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChamadoResponse> listarTodos(ChamadoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository
                .findAll(ChamadoSpecification.comFiltros(filtro, null), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<TopicoChamadoResponse> listarTopicos() {
        return topicoUseCases.listarDisponiveis();
    }

    @Transactional
    public ChamadoResponse atualizarAtendimento(UUID id, AtualizarAtendimentoChamadoRequest request,
                                                UUID usuarioOperadorId) {
        exigirUsuarioAutenticado(usuarioOperadorId);
        Chamado chamado = buscarEntity(id);
        EquipeAtendimento equipe = request.equipeId() == null
                ? null : equipeUseCases.buscarEntity(request.equipeId());
        chamado.atualizarAtendimento(request.status(), equipe, request.prioridade(), request.responsavelId(),
                request.solucao(), usuarioOperadorId);
        persistirAlteracoes();
        anunciar(chamado);
        return mapper.toResponse(chamado);
    }

    @Transactional
    public InteracaoChamadoResponse responderMeuChamado(UUID id, InteracaoChamadoRequest request,
                                                        UUID usuarioAutenticado) {
        exigirUsuarioAutenticado(usuarioAutenticado);
        Chamado chamado = repository.findByIdAndUsuarioSolicitanteId(id, usuarioAutenticado)
                .orElseThrow(() -> naoEncontrado(id));
        var interacao = chamado.responderPeloSolicitante(usuarioAutenticado, request.mensagem());
        persistirAlteracoes();
        anunciar(chamado);
        return mapper.toResponse(interacao);
    }

    @Transactional
    public InteracaoChamadoResponse adicionarInteracaoAtendimento(
            UUID id, InteracaoChamadoRequest request, UUID usuarioOperadorId) {
        exigirUsuarioAutenticado(usuarioOperadorId);
        Chamado chamado = buscarEntity(id);
        var interacao = chamado.registrarInteracaoAtendente(
                usuarioOperadorId, request.mensagem(), request.interno());
        persistirAlteracoes();
        anunciar(chamado);
        return mapper.toResponse(interacao);
    }

    @Transactional(readOnly = true)
    public List<InteracaoChamadoResponse> listarInteracoes(UUID id, UUID usuarioAutenticado) {
        Chamado chamado = buscarComAcesso(id, usuarioAutenticado);
        boolean incluirInternos = SecurityUtils.possuiAutoridade(PERMISSAO_GERENCIAR);
        return chamado.getInteracoes().stream()
                .filter(interacao -> incluirInternos || !interacao.isInterno())
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public ChamadoResponse reabrirMeuChamado(UUID id, ReabrirChamadoRequest request,
                                             UUID usuarioAutenticado) {
        exigirUsuarioAutenticado(usuarioAutenticado);
        Chamado chamado = repository.findByIdAndUsuarioSolicitanteId(id, usuarioAutenticado)
                .orElseThrow(() -> naoEncontrado(id));
        chamado.reabrirPeloSolicitante(usuarioAutenticado, request.motivo());
        persistirAlteracoes();
        anunciar(chamado);
        return mapper.toResponse(chamado);
    }

    @Transactional
    public ChamadoResponse cancelarMeuChamado(UUID id, UUID usuarioAutenticado) {
        exigirUsuarioAutenticado(usuarioAutenticado);
        Chamado chamado = repository.findByIdAndUsuarioSolicitanteId(id, usuarioAutenticado)
                .orElseThrow(() -> naoEncontrado(id));
        chamado.cancelarPeloSolicitante(usuarioAutenticado);
        return mapper.toResponse(repository.save(chamado));
    }

    @Transactional
    public AnexoChamadoResponse anexar(UUID id, UUID usuarioAutenticado, MultipartFile arquivo) {
        Chamado chamado = buscarComAcesso(id, usuarioAutenticado);
        validarTamanho(arquivo);
        String contentType = resolverContentType(arquivo);
        String nomeArquivo = nomeArquivoSeguro(arquivo, contentType);
        String objectKey = "chamados/" + chamado.getId() + "/" + UUID.randomUUID()
                + "." + TIPOS_PERMITIDOS.get(contentType);

        try (InputStream conteudo = arquivo.getInputStream()) {
            objectStorage.enviar(objectKey, conteudo, arquivo.getSize(), contentType);
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler a evidência enviada.", e);
        }

        try {
            AnexoChamado anexo = chamado.adicionarAnexo(nomeArquivo, contentType,
                    arquivo.getSize(), objectKey, usuarioAutenticado);
            persistirAlteracoes();
            return mapper.toResponse(anexo);
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(objectKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AnexoChamadoDownload baixarAnexo(UUID id, UUID idAnexo, UUID usuarioAutenticado) {
        Chamado chamado = buscarComAcesso(id, usuarioAutenticado);
        AnexoChamado anexo = chamado.getAnexos().stream()
                .filter(item -> item.isAtivo() && item.getId().equals(idAnexo))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evidência não encontrada."));
        StorageObject objeto = objectStorage.baixar(anexo.getObjectKey());
        return new AnexoChamadoDownload(objeto.conteudo(), anexo.getContentType(),
                anexo.getTamanhoBytes(), anexo.getNomeArquivo());
    }

    /**
     * Grava o que o domínio acabou de mudar, mantendo as instâncias em mãos.
     *
     * <p>É {@code flush()} e não {@code save()} de propósito, e a diferença não é
     * estética. O chamado já está gerenciado nesta transação, então {@code save()}
     * cairia em {@code merge()} — e o merge de uma coleção com filhos transientes
     * persiste <b>cópias</b>: as cópias recebem id e as instâncias que o domínio
     * acabou de devolver ficam sem nenhum. Tudo o que vem depois daqui precisa desses
     * ids: a interação devolvida na resposta, o anexo recém-criado e, sobretudo, a
     * marcação — que falha explicitamente quando não sabe em que texto foi escrita.</p>
     *
     * <p>Coberto por {@code ChamadoInteracaoPersistenciaTest}, que roda contra JPA de
     * verdade. Com repositório mockado este erro é invisível.</p>
     */
    private void persistirAlteracoes() {
        repository.flush();
    }

    /**
     * Traduz o que o domínio registrou em avisos e marcações.
     *
     * <p>É deliberadamente dirigido pelas interações, e não pelo método que foi
     * chamado: uma triagem só pode encaminhar, reatribuir e resolver de uma vez, e
     * quem sabe o que de fato aconteceu — inclusive o que <em>não</em> aconteceu por
     * já estar naquele estado — é a linha do tempo.</p>
     *
     * <p>Roda dentro da transação de propósito. As marcações podem recusar a operação
     * (marcar em comentário interno quem não o vê), e nesse caso a mensagem inteira
     * precisa ser desfeita — publicar antes de decidir isso deixaria um aviso sobre um
     * texto que não existe. Os eventos, esses, só saem depois do commit, porque passam
     * pelo outbox do Modulith.</p>
     */
    private void anunciar(Chamado chamado) {
        for (InteracaoChamado interacao : chamado.drenarNovasInteracoes()) {
            switch (interacao.getTipo()) {
                case ATRIBUICAO -> anunciarAtribuicao(chamado, interacao);
                case MENSAGEM_PUBLICA -> {
                    marcarPessoas(chamado, interacao);
                    anunciarMensagem(chamado, interacao);
                }
                // Comentário interno não vira aviso de mensagem — ele não é dirigido ao
                // solicitante —, mas uma marcação dentro dele é dirigida a alguém.
                case COMENTARIO_INTERNO -> marcarPessoas(chamado, interacao);
                case ALTERACAO_STATUS -> anunciarResolucao(chamado, interacao);
                case REABERTURA -> anunciarReabertura(chamado, interacao);
                default -> { /* abertura, prioridade, encaminhamento e anexos não avisam ninguém hoje */ }
            }
        }
    }

    private void anunciarAtribuicao(Chamado chamado, InteracaoChamado interacao) {
        // A remoção do responsável durante um encaminhamento também é ATRIBUICAO, com
        // destino nulo: não há a quem avisar.
        if (interacao.getResponsavelNovoId() == null) {
            return;
        }
        eventPublisher.publishEvent(new ChamadoAtribuidoEvent(
                interacao.getId(),
                chamado.getId(),
                chamado.getProtocolo(),
                chamado.getAssunto(),
                chamado.getPrioridade().name(),
                chamado.getEquipeAtendimento().getNome(),
                interacao.getResponsavelNovoId(),
                interacao.getUsuarioAutorId()));
    }

    private void anunciarMensagem(Chamado chamado, InteracaoChamado interacao) {
        eventPublisher.publishEvent(new ChamadoMensagemRecebidaEvent(
                interacao.getId(),
                chamado.getId(),
                chamado.getProtocolo(),
                chamado.getAssunto(),
                loginDe(interacao.getUsuarioAutorId()),
                resumir(interacao.getMensagem()),
                interacao.getUsuarioAutorId(),
                chamado.getUsuarioSolicitanteId(),
                chamado.getUsuarioResponsavelId()));
    }

    private void anunciarResolucao(Chamado chamado, InteracaoChamado interacao) {
        if (interacao.getStatusNovo() != StatusChamado.RESOLVIDO) {
            return;
        }
        eventPublisher.publishEvent(new ChamadoResolvidoEvent(
                interacao.getId(),
                chamado.getId(),
                chamado.getProtocolo(),
                chamado.getAssunto(),
                chamado.getSolucao(),
                chamado.getPrazoReaberturaHoras(),
                chamado.getUsuarioSolicitanteId(),
                interacao.getUsuarioAutorId()));
    }

    private void anunciarReabertura(Chamado chamado, InteracaoChamado interacao) {
        eventPublisher.publishEvent(new ChamadoReabertoEvent(
                interacao.getId(),
                chamado.getId(),
                chamado.getProtocolo(),
                chamado.getAssunto(),
                chamado.getUsuarioSolicitanteId(),
                chamado.getUsuarioResponsavelId(),
                interacao.getUsuarioAutorId()));
    }

    /**
     * O texto vai inteiro para o módulo de marcações, que é quem sabe reconhecer o
     * {@code @}. O chamado só informa onde a frase está e quem pode lê-la.
     */
    private void marcarPessoas(Chamado chamado, InteracaoChamado interacao) {
        mencaoApi.registrar(new ContextoMencao(
                OrigemMencao.CHAMADO_INTERACAO,
                interacao.getId(),
                chamado.getId(),
                "chamado " + chamado.getProtocolo() + " – " + chamado.getAssunto(),
                "/chamados/" + chamado.getId(),
                interacao.getUsuarioAutorId(),
                interacao.isInterno()), interacao.getMensagem());
    }

    private String loginDe(UUID usuarioId) {
        return usuarioId == null ? "Alguém"
                : usuarioApi.buscarNomePorId(usuarioId).orElse("Alguém");
    }

    private static String resumir(String mensagem) {
        if (mensagem == null) {
            return null;
        }
        String limpo = mensagem.replaceAll("\\s+", " ").trim();
        return limpo.length() <= LIMITE_TRECHO ? limpo
                : limpo.substring(0, LIMITE_TRECHO).trim() + "…";
    }

    private Chamado buscarComAcesso(UUID id, UUID usuarioAutenticado) {
        exigirUsuarioAutenticado(usuarioAutenticado);
        if (SecurityUtils.possuiAutoridade(PERMISSAO_GERENCIAR)) {
            return buscarEntity(id);
        }
        // Não revela se um protocolo de outro solicitante existe.
        return repository.findByIdAndUsuarioSolicitanteId(id, usuarioAutenticado)
                .orElseThrow(() -> naoEncontrado(id));
    }

    private Chamado buscarEntity(UUID id) {
        return repository.findById(id).orElseThrow(() -> naoEncontrado(id));
    }

    private static RecursoNaoEncontradoException naoEncontrado(UUID id) {
        return new RecursoNaoEncontradoException("Chamado não encontrado com ID: " + id);
    }

    private static void exigirUsuarioAutenticado(UUID usuarioId) {
        if (usuarioId == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        }
    }

    private static void validarTamanho(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie uma evidência não vazia.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("A evidência excede o limite de 15 MB.");
        }
    }

    private static String resolverContentType(MultipartFile arquivo) {
        String contentType = normalizar(arquivo.getContentType());
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType)) {
            contentType = inferirPorExtensao(arquivo.getOriginalFilename());
        }
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType)) {
            throw new InvarianteVioladaException(
                    "Tipo de evidência não suportado. Aceitos: imagens, PDF, TXT, Word e Excel.");
        }
        return contentType;
    }

    private static String nomeArquivoSeguro(MultipartFile arquivo, String contentType) {
        String original = arquivo.getOriginalFilename();
        String nome = StringUtils.hasText(original)
                ? StringUtils.getFilename(StringUtils.cleanPath(original))
                : "evidencia." + TIPOS_PERMITIDOS.get(contentType);
        if (!StringUtils.hasText(nome)) {
            nome = "evidencia." + TIPOS_PERMITIDOS.get(contentType);
        }
        return nome.length() <= 255 ? nome : nome.substring(nome.length() - 255);
    }

    private static String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        int separador = valor.indexOf(';');
        return (separador >= 0 ? valor.substring(0, separador) : valor).trim().toLowerCase();
    }

    private static String inferirPorExtensao(String nomeArquivo) {
        String extensao = normalizar(StringUtils.getFilenameExtension(nomeArquivo));
        if (extensao == null) return null;
        return switch (extensao) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt", "log" -> "text/plain";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> null;
        };
    }

    private void removerObjetoSilenciosamente(String objectKey) {
        try {
            objectStorage.remover(objectKey);
        } catch (RuntimeException ignored) {
            // A falha original é mais relevante; limpeza de órfãos pode ser reconciliada depois.
        }
    }
}
