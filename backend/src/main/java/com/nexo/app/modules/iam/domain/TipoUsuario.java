package com.nexo.app.modules.iam.domain;

import lombok.Getter;

/**
 * Define o tipo de usuário no sistema.
 */
@Getter
public enum TipoUsuario {
    ADMINISTRATIVO("Administrativo"),
    CAMPO("Campo");

    private final String descricao;

    TipoUsuario(String descricao) {
        this.descricao = descricao;
    }
}
