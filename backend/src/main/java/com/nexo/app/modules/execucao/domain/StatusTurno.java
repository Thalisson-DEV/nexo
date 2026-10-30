package com.nexo.app.modules.execucao.domain;

import lombok.Getter;

@Getter
public enum StatusTurno {
    ABERTO("Aberto"),
    FECHADO("Fechado");

    private final String descricao;

    StatusTurno(String descricao) {
        this.descricao = descricao;
    }
}
