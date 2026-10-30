package com.nexo.app.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando ocorre uma falha ao gravar, ler ou remover um objeto no
 * armazenamento externo (ex.: Cloudflare R2). Indica problema de infraestrutura,
 * não de regra de negócio.
 *
 * @author <b>Thalisson</b>
 */
public class ArmazenamentoException extends BaseBusinessException {

    public ArmazenamentoException(String detail) {
        super("Falha no Armazenamento de Arquivos", detail, HttpStatus.INTERNAL_SERVER_ERROR, "armazenamento-falha");
    }

    public ArmazenamentoException(String detail, Throwable cause) {
        super("Falha no Armazenamento de Arquivos", detail, HttpStatus.INTERNAL_SERVER_ERROR, "armazenamento-falha");
        this.initCause(cause);
    }
}
