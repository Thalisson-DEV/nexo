package com.nexo.app.modules.assistente.application.conexoes;

import org.springframework.http.HttpStatus;

/**
 * Erro no formato do OAuth ({@code {"error": "...", "error_description": "..."}}), que os clientes de
 * IA leem; nao passa pelo Problem Details do resto da API.
 */
public class OAuthErro extends RuntimeException {

    private final String codigo;
    private final HttpStatus status;

    public OAuthErro(String codigo, String descricao) {
        this(codigo, descricao, HttpStatus.BAD_REQUEST);
    }

    public OAuthErro(String codigo, String descricao, HttpStatus status) {
        super(descricao);
        this.codigo = codigo;
        this.status = status;
    }

    public String codigo() {
        return codigo;
    }

    public HttpStatus status() {
        return status;
    }
}
