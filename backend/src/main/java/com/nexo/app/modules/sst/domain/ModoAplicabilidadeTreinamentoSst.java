package com.nexo.app.modules.sst.domain;

public enum ModoAplicabilidadeTreinamentoSst {
    FUNCTION(true, false),
    BODY_ROLE(false, true),
    FUNCTION_AND_BODY_ROLE(true, true);

    private final boolean exigeFuncao;
    private final boolean exigePapel;

    ModoAplicabilidadeTreinamentoSst(boolean exigeFuncao, boolean exigePapel) {
        this.exigeFuncao = exigeFuncao;
        this.exigePapel = exigePapel;
    }

    public boolean exigeFuncao() {
        return exigeFuncao;
    }

    public boolean exigePapel() {
        return exigePapel;
    }
}
