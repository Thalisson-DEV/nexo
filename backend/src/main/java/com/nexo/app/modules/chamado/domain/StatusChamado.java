package com.nexo.app.modules.chamado.domain;

public enum StatusChamado {
    ABERTO,
    EM_ATENDIMENTO,
    AGUARDANDO_SOLICITANTE,
    RESOLVIDO,
    FECHADO,
    CANCELADO;

    public boolean isFinalizado() {
        return this == FECHADO || this == CANCELADO;
    }

    public boolean contaNoSla() {
        return this != RESOLVIDO && !isFinalizado();
    }
}
