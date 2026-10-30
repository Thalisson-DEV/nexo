package com.nexo.app.modules.assistente.application.llm;

/** Falha do provedor de modelo. A mensagem e segura para log (nunca contem a chave de API). */
public class LlmException extends RuntimeException {

    private final int status;
    private final boolean retryable;

    public LlmException(String message, int status, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public LlmException(String message, int status, boolean retryable, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryable = retryable;
    }

    public int status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }
}
