package com.nexo.app.modules.assistente.application.llm;

import java.util.List;

public record LlmResponse(
        String content,
        String reasoningContent,
        List<LlmToolCall> toolCalls,
        String finishReason,
        LlmUsage usage,
        String model) {

    public LlmResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? LlmUsage.EMPTY : usage;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public record LlmUsage(int promptTokens, int completionTokens, int cachedTokens, int reasoningTokens) {
        public static final LlmUsage EMPTY = new LlmUsage(0, 0, 0, 0);

        public LlmUsage plus(LlmUsage other) {
            if (other == null) return this;
            return new LlmUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens,
                    cachedTokens + other.cachedTokens, reasoningTokens + other.reasoningTokens);
        }
    }
}
