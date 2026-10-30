package com.nexo.app.modules.notificacao.application.usecases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoFilter;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.application.dto.PublicarComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.RelatorioConfirmacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.SegmentoComunicadoRequest;
import com.nexo.app.modules.notificacao.application.mappers.ComunicadoMapper;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.domain.InteracaoComunicado;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ComunicadoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ComunicadoSpecification;
import com.nexo.app.modules.notificacao.infrastructure.persistence.InteracaoComunicadoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;

/**
 * Administração do mural: escrever, definir público, publicar e prestar contas de
 * quem confirmou a leitura.
 *
 * <p>Sem cache: são poucas operações, todas de escrita ou de tela administrativa, e
 * a leitura quente (o mural do usuário) tem caminho próprio e depende de quem está
 * pedindo — um valor compartilhado em cache seria errado para todo mundo menos um.</p>
 */
@Service
@RequiredArgsConstructor
public class ComunicadoUseCases {

    /**
     * Chave usada só para localizar os anexos do informe na montagem do e-mail.
     *
     * <p>Não é um gatilho do catálogo — o mural publica direto no publicador, sem
     * passar pelo motor de regras. Ocupa o mesmo campo porque o que a porta de anexos
     * precisa saber é "de que tipo de registro é este id", e a resposta aqui é
     * comunicado.</p>
     */
    public static final String CHAVE_ANEXOS_COMUNICADO = "comunicado";

    private final ComunicadoRepository repository;
    private final InteracaoComunicadoRepository interacaoRepository;
    private final ComunicadoMapper mapper;
    private final ResolvedorAudienciaComunicado resolvedorAudiencia;
    private final PublicadorNotificacoes publicadorNotificacoes;
    private final UsuarioApi usuarioApi;

    @Transactional
    public ComunicadoResponse criar(ComunicadoRequest request) {
        Comunicado comunicado = new Comunicado(
                request.titulo(), request.resumo(), request.conteudo(), request.tipo(),
                request.apresentacao(), request.videoUrl(), request.acaoRotulo(),
                request.acaoDeeplink(), request.fixado(), request.exigeConfirmacao(),
                request.notificarNoSininho(), request.notificarPorEmail(),
                request.prioridadeNotificacao());

        aplicarSegmentos(comunicado, request.segmentos());
        return mapper.toResponse(repository.save(comunicado));
    }

    @Transactional
    public ComunicadoResponse atualizar(UUID id, ComunicadoRequest request) {
        Comunicado comunicado = buscarEntity(id);
        comunicado.atualizar(
                request.titulo(), request.resumo(), request.conteudo(), request.tipo(),
                request.apresentacao(), request.videoUrl(), request.acaoRotulo(),
                request.acaoDeeplink(), request.fixado(), request.exigeConfirmacao(),
                request.notificarNoSininho(), request.notificarPorEmail(),
                request.prioridadeNotificacao());

        if (request.segmentos() != null) {
            substituirSegmentos(comunicado, request.segmentos());
        }
        return mapper.toResponse(repository.save(comunicado));
    }

    /**
     * Correção de texto de um comunicado já no ar. Deliberadamente não mexe em
     * público nem em janela — ver {@link Comunicado#corrigirTexto}.
     */
    @Transactional
    public ComunicadoResponse corrigirTexto(UUID id, String titulo, String resumo, String conteudo) {
        Comunicado comunicado = buscarEntity(id);
        comunicado.corrigirTexto(titulo, resumo, conteudo);
        return mapper.toResponse(repository.save(comunicado));
    }

    /**
     * Coloca no ar e, se o autor pediu, também entrega no sininho de cada
     * destinatário.
     *
     * <p>O fan-out para a caixa de entrada acontece na publicação, e não na
     * exibição, porque é aqui que existe a intenção explícita. A chave de
     * idempotência é o próprio comunicado, então republicar ou reprocessar não gera
     * notificação repetida.</p>
     */
    @Transactional
    public ComunicadoResponse publicar(UUID id, PublicarComunicadoRequest request) {
        Comunicado comunicado = buscarEntity(id);
        comunicado.publicar(request.dataInicio(), request.dataFim());
        repository.save(comunicado);

        if (comunicado.isNotificarNoSininho()) {
            entregarNoSininho(comunicado);
        }
        return mapper.toResponse(comunicado);
    }

    @Transactional
    public ComunicadoResponse arquivar(UUID id) {
        Comunicado comunicado = buscarEntity(id);
        comunicado.arquivar();
        return mapper.toResponse(repository.save(comunicado));
    }

    @Transactional
    public void desativar(UUID id) {
        Comunicado comunicado = buscarEntity(id);
        comunicado.desativar();
        repository.save(comunicado);
    }

    @Transactional(readOnly = true)
    public PageResponse<ComunicadoResponse> listar(ComunicadoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository
                .findAll(ComunicadoSpecification.comFiltros(filtro), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ComunicadoResponse buscar(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    /**
     * Quem confirmou, quem só abriu e quem nunca apareceu.
     *
     * <p>O público é reexpandido no momento do relatório, o que significa que ele
     * reflete o quadro de usuários de hoje — quem entrou depois da publicação
     * aparece como pendente. Para prova de comunicação isso é o comportamento
     * desejado: a pergunta é quem ainda falta ler, não quem existia naquele dia.</p>
     */
    @Transactional(readOnly = true)
    public RelatorioConfirmacaoResponse relatorioConfirmacao(UUID id) {
        Comunicado comunicado = buscarEntity(id);
        Set<UUID> destinatarios = resolvedorAudiencia.resolver(comunicado);
        Map<UUID, String> logins = usuarioApi.buscarLoginsPorIds(destinatarios);

        Map<UUID, InteracaoComunicado> interacoes = interacaoRepository
                .findByComunicadoIdAndAtivoTrue(id).stream()
                .collect(java.util.stream.Collectors.toMap(
                        InteracaoComunicado::getIdUsuario, interacao -> interacao, (a, b) -> a));

        List<RelatorioConfirmacaoResponse.ConfirmacaoUsuario> confirmados = new ArrayList<>();
        List<RelatorioConfirmacaoResponse.PendenteConfirmacao> pendentes = new ArrayList<>();
        int visualizados = 0;

        for (UUID idUsuario : destinatarios) {
            InteracaoComunicado interacao = interacoes.get(idUsuario);
            String login = logins.getOrDefault(idUsuario, "(usuário removido)");

            if (interacao != null && interacao.getVisualizadoEm() != null) {
                visualizados++;
            }
            if (interacao != null && interacao.isConfirmado()) {
                confirmados.add(new RelatorioConfirmacaoResponse.ConfirmacaoUsuario(
                        idUsuario, login, interacao.getVisualizadoEm(), interacao.getConfirmadoEm()));
            } else {
                pendentes.add(new RelatorioConfirmacaoResponse.PendenteConfirmacao(
                        idUsuario, login, interacao == null ? null : interacao.getVisualizadoEm()));
            }
        }

        confirmados.sort(Comparator.comparing(RelatorioConfirmacaoResponse.ConfirmacaoUsuario::confirmadoEm));
        pendentes.sort(Comparator.comparing(RelatorioConfirmacaoResponse.PendenteConfirmacao::login));

        return new RelatorioConfirmacaoResponse(
                comunicado.getId(), comunicado.getTitulo(), comunicado.isExigeConfirmacao(),
                destinatarios.size(), confirmados.size(), visualizados, confirmados, pendentes);
    }

    public Comunicado buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comunicado não encontrado."));
    }

    private void entregarNoSininho(Comunicado comunicado) {
        Set<UUID> destinatarios = resolvedorAudiencia.resolver(comunicado);
        if (destinatarios.isEmpty()) {
            return;
        }
        publicadorNotificacoes.publicar(NovaNotificacao.builder()
                .destinatarios(destinatarios)
                .origem(OrigemNotificacao.COMUNICADO)
                .idReferencia(comunicado.getId())
                .categoria(CategoriaNotificacao.COMUNICADO)
                .prioridade(comunicado.getPrioridadeNotificacao())
                .titulo(comunicado.getTitulo())
                .corpo(resumoParaNotificacao(comunicado))
                .deeplink("/mural/" + comunicado.getId())
                .chaveIdempotencia("comunicado:" + comunicado.getId())
                .expiraEm(comunicado.getDataFim())
                .email(comunicado.isNotificarPorEmail())
                .chaveGatilho(CHAVE_ANEXOS_COMUNICADO)
                .build());
    }

    /**
     * O corpo da notificação é a chamada, não o comunicado inteiro: a caixa de
     * entrada leva ao mural, onde o conteúdo completo está formatado.
     */
    private String resumoParaNotificacao(Comunicado comunicado) {
        String base = comunicado.getResumo() != null ? comunicado.getResumo() : comunicado.getConteudo();
        String texto = base.replaceAll("\\s+", " ").trim();
        return texto.length() <= 1000 ? texto : texto.substring(0, 997) + "...";
    }

    private void substituirSegmentos(Comunicado comunicado, List<SegmentoComunicadoRequest> segmentos) {
        List<UUID> existentes = comunicado.segmentosAtivos().stream()
                .map(com.nexo.app.modules.notificacao.domain.SegmentoComunicado::getId)
                .toList();
        existentes.forEach(comunicado::removerSegmento);
        aplicarSegmentos(comunicado, segmentos);
    }

    private void aplicarSegmentos(Comunicado comunicado, List<SegmentoComunicadoRequest> segmentos) {
        if (segmentos == null || segmentos.isEmpty()) {
            return;
        }
        for (SegmentoComunicadoRequest segmento : segmentos) {
            comunicado.adicionarSegmento(segmento.tipoAlvo(), segmento.idAlvo(), segmento.valorAlvo());
        }
    }

    /**
     * Usado pelo mural para garantir que o comunicado sobre o qual o usuário está
     * agindo realmente está no ar — agir sobre um arquivado ou fora de janela
     * indicaria tela desatualizada.
     */
    Comunicado exigirVigente(UUID id, java.time.OffsetDateTime momento) {
        Comunicado comunicado = buscarEntity(id);
        if (!comunicado.estaVigente(momento)) {
            throw new InvarianteVioladaException("Este comunicado não está mais disponível.");
        }
        return comunicado;
    }
}
