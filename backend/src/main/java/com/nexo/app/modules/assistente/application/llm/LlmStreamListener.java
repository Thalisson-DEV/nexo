package com.nexo.app.modules.assistente.application.llm;

/** Recebe os deltas do streaming do modelo. O raciocinio nunca e repassado ao usuario. */
public interface LlmStreamListener {

    LlmStreamListener NOOP = new LlmStreamListener() { };

    default void onContentDelta(String delta) {
    }

    default void onReasoningDelta(String delta) {
    }

    /** Indica que o modelo comecou a montar a chamada de ferramenta {@code index} (nome ja conhecido). */
    default void onToolCallStarted(int index, String name) {
    }
}
