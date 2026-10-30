package com.nexo.app.modules.sst.domain;

/**
 * Define a quem a meta de inspeções se aplica. Uma meta de {@link #COLABORADOR}
 * é individual e prevalece sobre a meta da {@link #FUNCAO} do mesmo colaborador.
 */
public enum EscopoMeta {

    /** Vale para todos os colaboradores ativos que exercem a função. */
    FUNCAO,

    /** Vale apenas para o colaborador informado (exceção à meta da função). */
    COLABORADOR
}
