package com.nexo.app.modules.obras.domain;

/**
 * Situação exibida da programação, calculada a partir do status, dos itens e da data.
 */
public enum SituacaoProgramacao {
    PROGRAMADA,
    ATRASADA,
    EM_ANDAMENTO,
    CONCLUIDA,
    CANCELADA
}
