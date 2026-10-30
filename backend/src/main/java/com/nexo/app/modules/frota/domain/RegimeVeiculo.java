package com.nexo.app.modules.frota.domain;

/** Vínculo do veículo com a empresa: próprio ou locado de terceiro. */
public enum RegimeVeiculo {
    PROPRIO("Próprio"),
    LOCADO("Locado");

    private final String rotulo;

    RegimeVeiculo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
