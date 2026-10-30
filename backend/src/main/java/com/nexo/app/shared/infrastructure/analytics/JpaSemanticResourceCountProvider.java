package com.nexo.app.shared.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacao reutilizavel para contagens JPQL previamente registradas pelo servidor.
 * O modelo recebe apenas IDs semanticos; nunca recebe nem fornece JPQL.
 */
@Transactional(readOnly = true)
public class JpaSemanticResourceCountProvider implements SemanticResourceCountProvider {

    private final EntityManager entityManager;
    private final Map<String, ResourceDefinition> resources;

    public JpaSemanticResourceCountProvider(EntityManager entityManager, List<ResourceDefinition> definitions) {
        this.entityManager = entityManager;
        Map<String, ResourceDefinition> indexed = new LinkedHashMap<>();
        for (ResourceDefinition definition : definitions) {
            if (definition.id() == null || !definition.id().matches("[a-z][a-z0-9_]*")
                    || definition.nome() == null || definition.nome().isBlank()
                    || definition.descricao() == null || definition.descricao().isBlank()
                    || definition.autoridadeObrigatoria() == null || definition.autoridadeObrigatoria().isBlank()
                    || definition.jpql() == null || definition.jpql().isBlank()) {
                throw new IllegalArgumentException("Definicao de recurso contabilizavel invalida");
            }
            if (indexed.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Recurso contabilizavel duplicado: " + definition.id());
            }
        }
        this.resources = Map.copyOf(indexed);
    }

    @Override
    public List<ResourceCountDescriptor> descriptors() {
        return resources.values().stream()
                .map(item -> new ResourceCountDescriptor(
                        item.id(), item.nome(), item.descricao(), item.autoridadeObrigatoria()))
                .toList();
    }

    @Override
    public long count(String resourceId, SemanticQueryContext context) {
        ResourceDefinition definition = resources.get(resourceId);
        if (definition == null) {
            throw new IllegalArgumentException("Recurso indisponivel ou desconhecido");
        }
        if (!context.possuiAutoridade(definition.autoridadeObrigatoria())) {
            throw new AccessDeniedException("Recurso nao autorizado");
        }
        return entityManager.createQuery(definition.jpql(), Long.class).getSingleResult();
    }

    public record ResourceDefinition(
            String id,
            String nome,
            String descricao,
            String autoridadeObrigatoria,
            String jpql) {
    }
}
