package com.nexo.app.modules.notificacao.application.events;

import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;

/**
 * Uma notificação já gravada deve seguir também por e-mail.
 *
 * <p>Evento separado do {@code NotificacaoEntregueEvent} de propósito. Aquele existe
 * para o push em tempo real e vale para toda notificação; este só é publicado para as
 * poucas que passaram pelo crivo de canal, e carrega o que o push não precisa saber. Um
 * evento só, com um sinalizador dentro, faria o serviço de push acordar a cada e-mail e
 * o de e-mail a cada push.</p>
 *
 * <p>Pode carregar um ou mais destinatários gerados a partir do mesmo comando de publicação.
 * Para avisos distribuídos a vários usuários (ex: regras por limiar ou comunicados), os
 * destinatários seguem agrupados para permitir o despacho de um único e-mail com múltiplos
 * endereços no campo 'To'.</p>
 *
 * @param destinatarios destinatários elegíveis para receber o e-mail
 * @param notificacao modelo dos dados da notificação (título, corpo, contexto, etc.)
 * @param chaveGatilho gatilho de origem, para localizar os anexos do registro
 */
public record EmailSolicitadoEvent(
        List<DestinatarioNotificacao> destinatarios,
        NotificacaoResponse notificacao,
        String chaveGatilho
) {

    public EmailSolicitadoEvent {
        destinatarios = destinatarios == null ? List.of() : List.copyOf(destinatarios);
    }

    /**
     * Construtor de conveniência para notificações com destinatário único.
     */
    public EmailSolicitadoEvent(UUID idUsuario, NotificacaoResponse notificacao, String chaveGatilho) {
        this(List.of(new DestinatarioNotificacao(idUsuario, notificacao != null ? notificacao.id() : null)),
                notificacao, chaveGatilho);
    }

    /**
     * Devolve o primeiro id de usuário para compatibilidade quando há apenas um destinatário.
     */
    public UUID idUsuario() {
        return destinatarios.isEmpty() ? null : destinatarios.get(0).idUsuario();
    }
}

