package com.nexo.app.modules.assistente.application.llm;

/** Chamada de ferramenta pedida pelo modelo. {@code arguments} e o JSON bruto emitido pelo modelo. */
public record LlmToolCall(String id, String name, String arguments) {
}
