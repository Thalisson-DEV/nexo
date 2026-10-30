package com.nexo.app.modules.notificacao.infrastructure.messaging;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexo.app.modules.notificacao.application.events.NotificacaoEntregueEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Empurra a notificação recém-gravada para a sessão STOMP do destinatário.
 *
 * <p>Entrega no destino privado {@code /user/queue/notificacoes}: o Spring resolve
 * o {@code /user} para a sessão cujo {@code Principal} bate com o id informado, de
 * modo que a notificação nunca chega a outro assinante. O {@code Principal} é
 * estabelecido no handshake STOMP pelo interceptador de autenticação.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacaoPushService {

    static final String DESTINO = "/queue/notificacoes";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NotificacaoEntregueEvent evento) {
        try {
            messagingTemplate.convertAndSendToUser(
                    evento.idUsuario().toString(), DESTINO, evento.notificacao());
        } catch (MessagingException ex) {
            // O push é conveniência, não a entrega em si: a notificação já está
            // persistida e aparece na próxima leitura da caixa ou do resumo.
            log.warn("Falha ao empurrar a notificação {} para o usuário {}.",
                    evento.notificacao().id(), evento.idUsuario(), ex);
        }
    }
}
