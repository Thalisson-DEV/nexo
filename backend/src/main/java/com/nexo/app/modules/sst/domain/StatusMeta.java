package com.nexo.app.modules.sst.domain;

/**
 * Situação do colaborador diante da meta na janela apurada.
 */
public enum StatusMeta {

    /** Já cumpriu a quantidade exigida na janela. */
    ATINGIDA,

    /** Janela ainda aberta e quantidade exigida ainda não alcançada. */
    EM_ANDAMENTO,

    /** Janela encerrada sem alcançar a quantidade exigida. */
    NAO_ATINGIDA
}
