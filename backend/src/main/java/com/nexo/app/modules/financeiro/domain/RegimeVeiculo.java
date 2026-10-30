package com.nexo.app.modules.financeiro.domain;

/**
 * Vínculo do veículo com a empresa, relevante nas solicitações de frota porque
 * define quem arca com o custo da manutenção.
 */
public enum RegimeVeiculo implements Rotulavel {

    /** Veículo da frota própria. */
    PROPRIO("Próprio"),

    /** Veículo locado de terceiro. */
    LOCADO("Locado");

    private final String rotulo;

    RegimeVeiculo(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto exibido ao usuário; publicado no schema para a tela não replicá-lo. */
    @Override
    public String getRotulo() {
        return rotulo;
    }
}
