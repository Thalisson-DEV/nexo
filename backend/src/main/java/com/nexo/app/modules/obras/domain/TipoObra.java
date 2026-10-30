package com.nexo.app.modules.obras.domain;

/**
 * Classificacao operacional da obra.
 */
public enum TipoObra {
    OBRA,
    CORRETIVA,
    PREVENTIVA,
    LPT;

    public static TipoObra ouPadrao(TipoObra tipoObra) {
        return tipoObra != null ? tipoObra : OBRA;
    }

    public boolean exigeOcorrencia() {
        return this == CORRETIVA || this == PREVENTIVA;
    }
}
