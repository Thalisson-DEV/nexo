package com.nexo.app.modules.frota.domain;

/** Situação de uso de um veículo ou implemento. */
public enum SituacaoOperacional {
    ATIVO("Ativo"),
    EM_MANUTENCAO("Em manutenção"),
    PARADO("Parado"),
    RESERVA("Reserva"),
    BAIXADO("Baixado");

    private final String rotulo;

    SituacaoOperacional(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
