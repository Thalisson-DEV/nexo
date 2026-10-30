package com.nexo.app.modules.assistente.application.agent;

/**
 * Nivel de risco da ferramenta. Somente {@link #READ_ONLY} executa diretamente; os demais seguem
 * plano -> previa -> confirmacao do usuario -> execucao -> auditoria.
 */
public enum RiskLevel {
    READ_ONLY,
    WRITE_LOW,
    WRITE_HIGH;

    public boolean requiresConfirmation() {
        return this != READ_ONLY;
    }
}
