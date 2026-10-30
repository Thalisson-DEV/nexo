package com.nexo.app.modules.sst.domain;

public enum StatusReuniaoGovernancaSst {
    AGENDADA,
    EM_REALIZACAO,
    AGUARDANDO_ATA,
    AGUARDANDO_ASSINATURAS,
    FINALIZADA,
    CANCELADA;

    public boolean permiteConteudo() {
        return this == EM_REALIZACAO || this == AGUARDANDO_ATA || this == AGUARDANDO_ASSINATURAS;
    }
}
