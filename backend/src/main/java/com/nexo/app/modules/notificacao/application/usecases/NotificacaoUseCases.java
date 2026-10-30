package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoFilter;
import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.ResumoNotificacoesResponse;
import com.nexo.app.modules.notificacao.application.mappers.NotificacaoMapper;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.Notificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.NotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.NotificacaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * Caixa de entrada do usuário autenticado.
 *
 * <p>Todas as operações são escopadas pelo próprio usuário, resolvido aqui e não
 * recebido do cliente. Não há cache: a caixa muda a cada notificação recebida ou
 * lida, e um valor em cache seria um contador errado no sininho — exatamente o tipo
 * de erro que o usuário nota.</p>
 */
@Service
@RequiredArgsConstructor
public class NotificacaoUseCases {

    private final NotificacaoRepository repository;
    private final NotificacaoMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<NotificacaoResponse> listar(NotificacaoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository
                .findAll(NotificacaoSpecification.daCaixaDe(usuarioAtual(), filtro), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ResumoNotificacoesResponse resumo() {
        return montarResumo(usuarioAtual());
    }

    @Transactional
    public NotificacaoResponse marcarLida(UUID id) {
        Notificacao notificacao = buscarDoUsuario(id);
        notificacao.marcarLida();
        return mapper.toResponse(repository.save(notificacao));
    }

    @Transactional
    public NotificacaoResponse marcarNaoLida(UUID id) {
        Notificacao notificacao = buscarDoUsuario(id);
        notificacao.marcarNaoLida();
        return mapper.toResponse(repository.save(notificacao));
    }

    @Transactional
    public void arquivar(UUID id) {
        Notificacao notificacao = buscarDoUsuario(id);
        notificacao.arquivar();
        repository.save(notificacao);
    }

    /**
     * Devolve o resumo já atualizado: quem limpa a caixa quer ver o contador zerar,
     * e resolver isso na mesma chamada evita uma segunda ida ao servidor.
     */
    @Transactional
    public ResumoNotificacoesResponse marcarTodasLidas() {
        UUID idUsuario = usuarioAtual();
        repository.marcarTodasComoLidas(idUsuario, OffsetDateTime.now());
        return montarResumo(idUsuario);
    }

    @Transactional
    public ResumoNotificacoesResponse arquivarLidas() {
        UUID idUsuario = usuarioAtual();
        repository.arquivarLidas(idUsuario, OffsetDateTime.now());
        return montarResumo(idUsuario);
    }

    private ResumoNotificacoesResponse montarResumo(UUID idUsuario) {
        OffsetDateTime agora = OffsetDateTime.now();
        Map<CategoriaNotificacao, Long> porCategoria = new EnumMap<>(CategoriaNotificacao.class);
        repository.contarNaoLidasPorCategoria(idUsuario, agora)
                .forEach(contagem -> porCategoria.put(contagem.getCategoria(), contagem.getTotal()));
        return new ResumoNotificacoesResponse(repository.contarNaoLidas(idUsuario, agora), porCategoria);
    }

    /**
     * Notificação de outro usuário é tratada como inexistente, e não como acesso
     * negado: responder 403 confirmaria que aquele id existe.
     */
    private Notificacao buscarDoUsuario(UUID id) {
        return repository.findByIdAndIdUsuarioAndAtivoTrue(id, usuarioAtual())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação não encontrada."));
    }

    private UUID usuarioAtual() {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        }
        return idUsuario;
    }
}
