package com.nexo.app.modules.assistente.application.llm;

import java.util.List;

public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools,
        ToolChoice toolChoice,
        Double temperature,
        Integer maxTokens,
        Boolean parallelToolCalls,
        String reasoningEffort) {

    public enum ToolChoice { AUTO, NONE, REQUIRED }

    public LlmRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolChoice = toolChoice == null ? ToolChoice.AUTO : toolChoice;
    }
}
