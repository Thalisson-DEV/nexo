package com.nexo.app.modules.notificacao.domain;

/**
 * Quem produziu a notificação. Distingue as três fontes que gravam na mesma caixa
 * de entrada, permitindo ao front tratar cada uma como merece (o comunicado abre o
 * mural, a regra leva ao registro de origem).
 */
public enum OrigemNotificacao {

    /** Motor de regras: disparada por evento de domínio ou por limiar atingido. */
    REGRA,

    /** Mural: um administrador publicou um comunicado direcionado a este usuário. */
    COMUNICADO,

    /** Emitida pela própria plataforma, sem regra configurável por trás. */
    SISTEMA,
    /** Avisos publicados por uma automação (motor que substituiu as regras). */
    AUTOMACAO
}
