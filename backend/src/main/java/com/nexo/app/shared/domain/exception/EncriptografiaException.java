package com.nexo.app.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando ocorre uma falha crítica nos processos de criptografia ou descriptografia.
 * 
 * @author <b>Thalisson</b>
 */
public class EncriptografiaException extends BaseBusinessException {
    
    public EncriptografiaException(String detail) {
        super("Erro de Segurança/Criptografia", detail, HttpStatus.INTERNAL_SERVER_ERROR, "security-encryption-error");
    }

    public EncriptografiaException(String detail, Throwable cause) {
        super("Erro de Segurança/Criptografia", detail, HttpStatus.INTERNAL_SERVER_ERROR, "security-encryption-error");
        this.initCause(cause);
    }
}
