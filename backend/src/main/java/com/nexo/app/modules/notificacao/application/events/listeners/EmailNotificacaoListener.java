package com.nexo.app.modules.notificacao.application.events.listeners;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexo.app.modules.notificacao.application.events.EmailSolicitadoEvent;
import com.nexo.app.modules.notificacao.application.usecases.EnvioEmailNotificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Leva ao canal de e-mail as notificações que passaram pelo crivo do publicador.
 *
 * <p>Irmão do {@code NotificacaoPushService} e pelas mesmas razões. {@code AFTER_COMMIT}
 * porque nenhuma falha de e-mail pode desfazer a operação de negócio — o desconto
 * continua registrado ainda que o aviso não saia. {@code @Async} porque o envio fala
 * com um servidor externo e baixa anexos do armazenamento: segurar a requisição HTTP
 * do usuário por isso transformaria "registrei o desconto" em uma espera de segundos
 * que ele não pediu. Com virtual threads o custo dessa espera é ocioso, não um
 * trabalhador bloqueado.</p>
 *
 * <p>A exceção é engolida com registro no log de propósito: a esta altura a
 * notificação já está no sininho e o {@code LogEmail} guarda o que houve. Deixar
 * subir só produziria um erro sem dono numa thread de background.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificacaoListener {

    private final EnvioEmailNotificacao envio;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmailSolicitadoEvent evento) {
        try {
            if (evento.destinatarios() != null && !evento.destinatarios().isEmpty()) {
                envio.enviar(evento.destinatarios(), evento.notificacao(), evento.chaveGatilho());
            } else if (evento.idUsuario() != null) {
                envio.enviar(evento.idUsuario(), evento.notificacao(), evento.chaveGatilho());
            }
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao enviar por e-mail a notificação {} para {} destinatários.",
                    evento.notificacao() != null ? evento.notificacao().id() : null,
                    evento.destinatarios() != null ? evento.destinatarios().size() : 0, e);
        }
    }
}
