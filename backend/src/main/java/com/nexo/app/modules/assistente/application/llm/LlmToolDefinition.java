package com.nexo.app.modules.assistente.application.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** Definicao de ferramenta enviada ao modelo (nome, descricao e JSON Schema dos parametros). */
public record LlmToolDefinition(String name, String description, JsonNode parameters) {
}
