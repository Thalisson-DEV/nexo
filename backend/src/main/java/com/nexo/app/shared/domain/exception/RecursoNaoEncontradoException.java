package com.nexo.app.shared.domain.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends BaseBusinessException {
    public RecursoNaoEncontradoException(String detail) {
        super("Recurso Não Encontrado", detail, HttpStatus.NOT_FOUND, "recurso-não-encontrado");
    }
}