package com.nexo.app.modules.notificacao.application.events;

import java.util.UUID;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;

/**
 * Sinaliza que uma notificação foi gravada para um destinatário.
 *
 * <p>Evento interno do módulo, consumido apenas pelo push em tempo real. Existe
 * para que o envio pelo WebSocket aconteça depois do commit: empurrar durante a
 * transação faria o usuário ver na tela uma notificação que um rollback posterior
 * apagaria do banco.</p>
 */
public record NotificacaoEntregueEvent(UUID idUsuario, NotificacaoResponse notificacao) {
}
