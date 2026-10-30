package com.nexo.app.shared.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Exceção base para todas as violações de regras de negócio.
 * 
 * @author <b>Thalisson</b>
 */
@Getter
public abstract class BaseBusinessException extends RuntimeException {
    
    private final String title;
    private final HttpStatus status;
    private final URI type;

    protected BaseBusinessException(String title, String detail, HttpStatus status, String typePath) {
        super(detail);
        this.title = title;
        this.status = status;
        this.type = URI.create("https://app.exemplo.com/errors/" + typePath);
    }
}
