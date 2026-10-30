package com.nexo.app.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * A sessão do aparelho não pode mais ser renovada (expirou, foi encerrada, revogada ou o
 * token não confere). É o ÚNICO sinal que autoriza o frontend a pedir um novo login:
 * token de acesso vencido, falha de rede e 5xx nunca encerram a sessão local.
 */
public class SessaoInvalidaException extends BaseBusinessException {

    public static final String CODIGO = "SESSAO_INVALIDA";

    public SessaoInvalidaException(String detail) {
        super("Sessão Encerrada", detail, HttpStatus.UNAUTHORIZED, "sessao-invalida");
    }
}
