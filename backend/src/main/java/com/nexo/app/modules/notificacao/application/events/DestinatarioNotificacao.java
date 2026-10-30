package com.nexo.app.modules.notificacao.application.events;

import java.util.UUID;

/**
 * Par de destinatário e respectiva notificação gerada para consolidação de envio de e-mails.
 *
 * @param idUsuario ID do usuário destinatário
 * @param idNotificacao ID da notificação persistida individualmente para o usuário
 */
public record DestinatarioNotificacao(UUID idUsuario, UUID idNotificacao) {
}
