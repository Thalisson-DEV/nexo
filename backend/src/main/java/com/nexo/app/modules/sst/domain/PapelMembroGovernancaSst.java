package com.nexo.app.modules.sst.domain;

/**
 * Papéis permanentes conhecidos pelo produto. Secretário não pertence a este enum:
 * sua designação ocorre em cada reunião.
 */
public enum PapelMembroGovernancaSst {
    PRESIDENTE(TipoCorpoGovernancaSst.CIPA),
    VICE_PRESIDENTE(TipoCorpoGovernancaSst.CIPA),
    MEMBRO(TipoCorpoGovernancaSst.CIPA),
    REPRESENTANTE_NOMEADO(TipoCorpoGovernancaSst.CIPA),
    COORDENADOR_GERAL(TipoCorpoGovernancaSst.BRIGADA),
    CHEFE(TipoCorpoGovernancaSst.BRIGADA),
    LIDER(TipoCorpoGovernancaSst.BRIGADA),
    BRIGADISTA(TipoCorpoGovernancaSst.BRIGADA);

    private final TipoCorpoGovernancaSst tipo;

    PapelMembroGovernancaSst(TipoCorpoGovernancaSst tipo) {
        this.tipo = tipo;
    }

    public boolean pertenceA(TipoCorpoGovernancaSst tipoCorpo) {
        return tipo == tipoCorpo;
    }
}
