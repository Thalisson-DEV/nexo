package com.nexo.app.modules.sst.domain;

/**
 * Espécie da Comunicação de Acidente de Trabalho emitida para o evento, nos termos
 * do formulário oficial do INSS.
 */
public enum TipoCat {

    /** Primeira comunicação do acidente. */
    INICIAL("Inicial"),

    /** Reabertura por agravamento ou sequela do mesmo acidente. */
    REABERTURA("Reabertura"),

    /** Comunicação de óbito decorrente do acidente já comunicado. */
    COMUNICACAO_OBITO("Comunicação de óbito");

    private final String rotulo;

    TipoCat(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
