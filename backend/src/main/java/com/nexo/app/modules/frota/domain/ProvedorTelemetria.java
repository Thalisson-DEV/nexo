package com.nexo.app.modules.frota.domain;

/** Fornecedores de telemetria que podem estar instalados no veículo. */
public enum ProvedorTelemetria {
    UBICALY("Vivo (Ubicaly)"),
    ZUQ("Zuq");

    private final String rotulo;

    ProvedorTelemetria(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
