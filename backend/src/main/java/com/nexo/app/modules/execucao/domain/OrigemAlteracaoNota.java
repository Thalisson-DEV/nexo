package com.nexo.app.modules.execucao.domain;

/**
 * De onde veio a mudança registrada na linha do tempo da nota.
 */
public enum OrigemAlteracaoNota {
    /** Equipe da própria nota, pelo app de campo. */
    CAMPO,
    /** Usuário administrativo, pelo sistema web. */
    ESCRITORIO
}
