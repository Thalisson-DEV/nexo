package com.nexo.app.modules.frota.domain;

/** Combustível principal do veículo; base para consumo e emissão de CO₂. */
public enum Combustivel {
    DIESEL_S10("Diesel S10"),
    DIESEL_S500("Diesel S500"),
    GASOLINA("Gasolina"),
    ETANOL("Etanol"),
    FLEX("Flex"),
    GNV("GNV"),
    ELETRICO("Elétrico");

    private final String rotulo;

    Combustivel(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
