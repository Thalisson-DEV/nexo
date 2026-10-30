package com.nexo.app.modules.assistente.application;

import com.nexo.app.shared.application.analytics.SemanticDatasetDescriptor;
import com.nexo.app.shared.application.analytics.SemanticDatasetProvider;
import com.nexo.app.shared.application.analytics.SemanticQuery;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.analytics.SemanticQueryResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemanticDatasetRegistry {

    private final Map<String, SemanticDatasetProvider> providers;

    public SemanticDatasetRegistry(List<SemanticDatasetProvider> providers) {
        Map<String, SemanticDatasetProvider> indexed = new LinkedHashMap<>();
        for (SemanticDatasetProvider provider : providers) {
            SemanticDatasetProvider duplicate = indexed.put(provider.descriptor().id(), provider);
            if (duplicate != null) {
                throw new IllegalStateException("Dataset semantico duplicado: " + provider.descriptor().id());
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public List<SemanticDatasetDescriptor> listarAutorizados(SemanticQueryContext context) {
        return providers.values().stream()
                .map(SemanticDatasetProvider::descriptor)
                .filter(descriptor -> context.possuiAutoridade(descriptor.autoridadeObrigatoria()))
                .toList();
    }

    public SemanticDatasetDescriptor descrever(String dataset, SemanticQueryContext context) {
        SemanticDatasetProvider provider = provider(dataset);
        exigirAutoridade(provider.descriptor(), context);
        return provider.descriptor();
    }

    public SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context) {
        SemanticDatasetProvider provider = provider(query.dataset());
        exigirAutoridade(provider.descriptor(), context);
        return provider.executar(query, context);
    }

    private SemanticDatasetProvider provider(String dataset) {
        SemanticDatasetProvider provider = providers.get(dataset);
        if (provider == null) {
            throw new IllegalArgumentException("Dataset indisponivel ou desconhecido");
        }
        return provider;
    }

    private void exigirAutoridade(SemanticDatasetDescriptor descriptor, SemanticQueryContext context) {
        if (!context.possuiAutoridade(descriptor.autoridadeObrigatoria())) {
            throw new AccessDeniedException("Dataset nao autorizado");
        }
    }
}
