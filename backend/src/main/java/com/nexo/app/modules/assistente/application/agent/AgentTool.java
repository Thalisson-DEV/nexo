package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Set;

/**
 * Ferramenta atomica do agente (equivalente ao {@code Tool} do Onyx). Toda ferramenta declara:
 * nome e descricao para o modelo, JSON Schema de entrada (e opcionalmente de saida), permissoes exigidas,
 * nivel de risco, timeout e o executor. A autorizacao e sempre validada no backend: uma ferramenta nunca
 * da ao agente acesso que o usuario nao possui.
 */
public interface AgentTool {

    /** Nome exposto ao modelo (snake_case, estavel). */
    String name();

    /** Nome curto para a interface ("Consultar movimentacoes"). */
    String displayName();

    /** Descricao exposta ao modelo: quando usar, o que retorna, limites. */
    String description();

    JsonNode inputSchema();

    default JsonNode outputSchema() {
        return null;
    }

    ToolKind kind();

    default RiskLevel riskLevel() {
        return RiskLevel.READ_ONLY;
    }

    /** Permissoes do Nexo exigidas: basta possuir qualquer uma. Vazio = qualquer usuario do assistente. */
    default Set<String> requiredAuthorities() {
        return Set.of();
    }

    default Duration timeout() {
        return Duration.ofSeconds(60);
    }

    /** Limite proprio de caracteres devolvidos ao modelo; 0 usa o limite padrao da configuracao. */
    default int maxResultChars() {
        return 0;
    }

    /** Disponibilidade dinamica (ex.: sandbox Python saudavel, provedor de busca configurado). */
    default boolean isAvailable(AgentRunContext context) {
        return true;
    }

    /** Texto objetivo exibido enquanto a ferramenta executa ("Consultando estoque"). Nunca raciocinio. */
    default String progressLabel(JsonNode arguments) {
        return displayName();
    }

    /** Argumentos seguros para exibir na timeline (ex.: SQL, codigo, consultas). */
    default JsonNode displayArguments(JsonNode arguments) {
        return arguments;
    }

    /**
     * Se a ferramenta entra na lista enviada ao modelo no chat. Ferramentas so de integracao (ex.: um
     * cadastro especifico exposto ao MCP, que no chat passa pela ferramenta generica) devolvem false.
     */
    default boolean oferecidaNoChat() {
        return true;
    }

    ToolResult execute(JsonNode arguments, ToolContext context) throws ToolException;
}
