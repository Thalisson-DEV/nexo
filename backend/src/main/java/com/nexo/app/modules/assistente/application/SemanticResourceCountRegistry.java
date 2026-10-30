package com.nexo.app.modules.assistente.application;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider.ResourceCountDescriptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemanticResourceCountRegistry {

    private final Map<String, RegisteredResource> resources;

    public SemanticResourceCountRegistry(List<SemanticResourceCountProvider> providers) {
        Map<String, RegisteredResource> indexed = new LinkedHashMap<>();
        for (SemanticResourceCountProvider provider : providers) {
            for (ResourceCountDescriptor descriptor : provider.descriptors()) {
                if (indexed.put(descriptor.id(), new RegisteredResource(descriptor, provider)) != null) {
                    throw new IllegalStateException("Recurso contabilizavel duplicado: " + descriptor.id());
                }
            }
        }
        this.resources = Map.copyOf(indexed);
    }

    public List<ResourceCountDescriptor> listarAutorizados(SemanticQueryContext context) {
        return resources.values().stream()
                .map(RegisteredResource::descriptor)
                .filter(item -> context.possuiAutoridade(item.autoridadeObrigatoria()))
                .toList();
    }

    public long contar(String resourceId, SemanticQueryContext context) {
        RegisteredResource resource = resources.get(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("Recurso indisponivel ou desconhecido");
        }
        if (!context.possuiAutoridade(resource.descriptor().autoridadeObrigatoria())) {
            throw new AccessDeniedException("Recurso nao autorizado");
        }
        return resource.provider().count(resourceId, context);
    }

    private record RegisteredResource(
            ResourceCountDescriptor descriptor,
            SemanticResourceCountProvider provider) {
    }
}
