package com.nexo.app.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando uma regra de negócio (invariante) do domínio é violada.
 * Ex: Saldo negativo não permitido, desativação de coordenador ativo, etc.
 * 
 * @author <b>Thalisson</b>
 */
public class InvarianteVioladaException extends BaseBusinessException {
    
    public InvarianteVioladaException(String detail) {
        super("Violação de Invariante de Negócio", detail, HttpStatus.UNPROCESSABLE_CONTENT, "invariante-violada");
    }
}
