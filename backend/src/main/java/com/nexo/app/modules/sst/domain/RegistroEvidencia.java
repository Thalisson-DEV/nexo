package com.nexo.app.modules.sst.domain;

/**
 * Disponibilidade de um registro objetivo do evento — telemetria do veículo ou
 * filmagem do local.
 *
 * <p>Os três valores existem porque "não há filmagem" e "não havia câmera ali" são
 * respostas diferentes: a primeira é uma perda de evidência, a segunda é uma
 * característica do local. Um booleano apagaria essa distinção justamente onde ela
 * orienta investimento em monitoramento.</p>
 */
public enum RegistroEvidencia {

    SIM("Sim"),
    NAO("Não"),
    NAO_APLICAVEL("Não aplicável");

    private final String rotulo;

    RegistroEvidencia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
