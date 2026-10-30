package com.nexo.app.modules.sst.domain;

/**
 * Tipos de resposta suportados por uma {@link Pergunta} de um formulário de inspeção.
 *
 * <p>Tipos objetivos (que oferecem opções pré-cadastradas) permitem marcar quais
 * opções geram um desvio de segurança. Tipos subjetivos coletam um valor livre
 * digitado pelo inspetor (texto, número ou data) e não possuem opções.</p>
 */
public enum TipoResposta {

    /** Escolha de exatamente uma opção dentre as cadastradas (ex.: Sim/Não/N.A.). */
    MULTIPLA_ESCOLHA(true),

    /** Seleção de uma ou mais opções dentre as cadastradas. */
    MULTIPLA_SELECAO(true),

    /** Campo de observação / texto livre. */
    TEXTO(false),

    /** Valor numérico (ex.: medições). */
    NUMERO(false),

    /** Data informada pelo inspetor. */
    DATA(false);

    private final boolean permiteOpcoes;

    TipoResposta(boolean permiteOpcoes) {
        this.permiteOpcoes = permiteOpcoes;
    }

    /**
     * Indica se este tipo de resposta admite opções pré-cadastradas (e, portanto,
     * a marcação de geração de desvio).
     */
    public boolean permiteOpcoes() {
        return permiteOpcoes;
    }
}
