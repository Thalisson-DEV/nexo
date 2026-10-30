package com.nexo.app.modules.assistente.application.agent;

/**
 * Erro esperado de ferramenta (parametro invalido, recurso inexistente, permissao ausente...). A mensagem
 * e devolvida ao modelo para que ele corrija a chamada; nunca contem detalhes internos sensiveis.
 */
public class ToolException extends RuntimeException {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
