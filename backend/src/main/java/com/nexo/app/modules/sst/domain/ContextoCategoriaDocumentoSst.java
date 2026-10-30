package com.nexo.app.modules.sst.domain;

public enum ContextoCategoriaDocumentoSst {
    CIPA,
    BRIGADA,
    AMBOS;

    public boolean aceita(TipoCorpoGovernancaSst tipoCorpo) {
        if (tipoCorpo == null) {
            return false;
        }
        return this == AMBOS || name().equals(tipoCorpo.name());
    }
}
