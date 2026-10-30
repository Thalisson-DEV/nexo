package com.nexo.app.modules.financeiro.domain;

/**
 * Estágio de curadoria de um {@link Fornecedor}.
 *
 * <p>O solicitante pode cadastrar um fornecedor novo no meio do preenchimento —
 * ele nasce {@link #PENDENTE} e já pode ser usado, para não travar a operação.
 * O financeiro depois complementa/confere os dados e o homologa.</p>
 */
public enum StatusFornecedor {

    /** Criado por um solicitante e ainda não conferido pelo financeiro. */
    PENDENTE(true),

    /** Dados conferidos pelo financeiro. */
    HOMOLOGADO(true),

    /** Impedido de receber novas solicitações. */
    BLOQUEADO(false);

    private final boolean utilizavel;

    StatusFornecedor(boolean utilizavel) {
        this.utilizavel = utilizavel;
    }

    /** Indica se novas solicitações podem apontar para um fornecedor neste status. */
    public boolean isUtilizavel() {
        return utilizavel;
    }
}
