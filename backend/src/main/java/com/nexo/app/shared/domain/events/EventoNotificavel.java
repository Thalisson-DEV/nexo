package com.nexo.app.shared.domain.events;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contrato de um evento de domínio que pode disparar regras de notificação.
 *
 * <p>Um módulo de negócio publica o evento e segue a vida — ele não sabe que
 * existem regras, nem quem será notificado, nem se alguém será. Quem decide isso é
 * o motor de notificações, a partir das regras que os administradores configuraram
 * para a mesma {@link #chaveGatilho()}.</p>
 *
 * <p>O evento carrega os {@link #papeis()} já resolvidos porque só o módulo de
 * origem sabe quem é o "solicitante" ou o "responsável" naquele contexto. Fazer o
 * motor descobrir isso exigiria que ele conhecesse o domínio de cada módulo —
 * exatamente o acoplamento que a modularização evita.</p>
 */
public interface EventoNotificavel {

    /**
     * Identifica o gatilho no catálogo (ex.: {@code "desconto.solicitado"}).
     * Precisa corresponder a uma constante de {@code GatilhoNotificacao}.
     */
    String chaveGatilho();

    /** Agregado que originou o evento — vira o {@code idReferencia} da notificação. */
    UUID idReferencia();

    /**
     * Dados disponíveis para as condições e para os textos das regras. As chaves
     * precisam estar entre as variáveis declaradas pelo gatilho no catálogo.
     */
    Map<String, Object> contexto();

    /**
     * Papéis do evento e quem os ocupa (ex.: {@code "solicitante"} → id do usuário).
     * É o que permite a uma regra endereçar "quem pediu" sem lista fixa.
     */
    Map<String, Set<UUID>> papeis();
}
