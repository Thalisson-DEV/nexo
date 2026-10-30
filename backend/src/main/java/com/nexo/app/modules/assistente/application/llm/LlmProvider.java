package com.nexo.app.modules.assistente.application.llm;

import java.util.function.BooleanSupplier;

/**
 * Porta de modelo do Copiloto. O runtime agentic depende apenas desta abstracao; trocar de provedor
 * (Fireworks, OpenAI, Azure, OpenRouter, Ollama...) nao exige reescrever o loop.
 */
public interface LlmProvider {

    boolean isConfigured();

    String defaultModel();

    /**
     * Executa uma chamada em streaming, entregando deltas ao listener, e devolve a resposta agregada.
     * Deve retornar prontamente quando {@code cancelled} passar a ser verdadeiro.
     */
    LlmResponse stream(LlmRequest request, LlmStreamListener listener, BooleanSupplier cancelled);

    /** Chamada simples sem streaming (fluxos secundarios, como titulo da conversa). */
    LlmResponse complete(LlmRequest request);
}
