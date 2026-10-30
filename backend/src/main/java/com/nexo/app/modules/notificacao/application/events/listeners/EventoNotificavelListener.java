package com.nexo.app.modules.notificacao.application.events.listeners;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.nexo.app.modules.notificacao.application.usecases.MotorNotificacoes;
import com.nexo.app.shared.domain.events.EventoNotificavel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ponte única entre os eventos de domínio e o motor de regras.
 *
 * <p>Um listener só, sobre a interface, atende todos os gatilhos por ocorrência —
 * presentes e futuros. Um módulo que passe a publicar um evento novo não exige
 * nenhuma alteração aqui: basta a chave estar no catálogo e existir regra
 * configurada para ela.</p>
 *
 * <p>{@code @ApplicationModuleListener} faz a entrega passar pela tabela
 * {@code event_publication} do Spring Modulith, depois do commit de quem publicou.
 * Isso significa que uma falha ao notificar nunca desfaz a operação de negócio — o
 * desconto continua registrado mesmo que o aviso falhe — e que a entrega é
 * retentável, o que só é seguro porque o publicador é idempotente.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventoNotificavelListener {

    private final MotorNotificacoes motor;

    @ApplicationModuleListener
    public void on(EventoNotificavel evento) {
        int entregues = motor.processarEvento(evento);
        log.debug("Gatilho '{}' referente a {} entregou {} notificações.",
                evento.chaveGatilho(), evento.idReferencia(), entregues);
    }
}
