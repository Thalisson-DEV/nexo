package com.nexo.app.modules.frota.domain;

/** Documentos acompanhados pela frota. */
public enum TipoDocumentoFrota {
    CRLV("CRLV"),
    LICENCIAMENTO("Licenciamento"),
    IPVA("IPVA"),
    SEGURO("Seguro"),
    TACOGRAFO("Aferição do tacógrafo"),
    ANTT("ANTT"),
    INSPECAO_VEICULAR("Inspeção veicular"),
    LAUDO_IMPLEMENTO("Laudo do implemento"),
    CONTRATO_LOCACAO("Contrato de locação"),
    OUTRO("Outro");

    private final String rotulo;

    TipoDocumentoFrota(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
