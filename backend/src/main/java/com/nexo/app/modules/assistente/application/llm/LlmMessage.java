package com.nexo.app.modules.assistente.application.llm;

import java.util.List;

/**
 * Mensagem no formato neutro do runtime (equivalente ao formato OpenAI Chat Completions, lingua franca
 * dos provedores — a mesma escolha do Onyx via LiteLLM). O adaptador do provedor traduz para o wire.
 */
public sealed interface LlmMessage {

    String content();

    record System(String content) implements LlmMessage {
    }

    record User(String content, List<Image> images) implements LlmMessage {
        public User {
            images = images == null ? List.of() : List.copyOf(images);
        }

        public User(String content) {
            this(content, List.of());
        }
    }

    /**
     * Mensagem do assistente. {@code reasoningContent} so e preenchido para passos do turno atual que
     * chamaram ferramentas (raciocinio intercalado exigido por alguns modelos); nunca e exibido ao usuario
     * nem persistido.
     */
    record Assistant(String content, String reasoningContent, List<LlmToolCall> toolCalls) implements LlmMessage {
        public Assistant {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public Assistant(String content) {
            this(content, null, List.of());
        }
    }

    record Tool(String toolCallId, String name, String content) implements LlmMessage {
    }

    record Image(String mimeType, String base64) {
    }
}
