package com.nexo.app.shared.application.analytics;

/** Implementado pelo modulo que possui os dados e executa suas consultas. */
public interface SemanticDatasetProvider {
    SemanticDatasetDescriptor descriptor();
    SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context);
}
