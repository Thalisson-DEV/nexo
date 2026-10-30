package com.nexo.app.modules.assistente.domain;

/** Superficie do Nexo onde a conversa com o Copiloto comecou. O agente e o mesmo em todas. */
public enum OrigemConversa {
    WORKSPACE,
    DASHBOARD,
    /** Conversa tecnica de uma conexao de IA externa (MCP); fora do historico do Copiloto. */
    MCP
}
