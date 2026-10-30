package com.nexo.app.modules.assistente.application;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticResourceCountRegistryTest {

    @Test
    void omiteRecursoSemPermissaoERevalidaAoContar() {
        SemanticResourceCountRegistry registry = new SemanticResourceCountRegistry(List.of(new StubProvider()));
        SemanticQueryContext allowed = context("iam:usuario:read");
        SemanticQueryContext denied = context();

        assertThat(registry.listarAutorizados(allowed)).extracting("id").containsExactly("usuarios");
        assertThat(registry.listarAutorizados(denied)).isEmpty();
        assertThat(registry.contar("usuarios", allowed)).isEqualTo(42);
        assertThatThrownBy(() -> registry.contar("usuarios", denied))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejeitaIdsDuplicadosEntreModulos() {
        assertThatThrownBy(() -> new SemanticResourceCountRegistry(List.of(new StubProvider(), new StubProvider())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicado");
    }

    private static SemanticQueryContext context(String... authorities) {
        return new SemanticQueryContext(UUID.randomUUID(), Set.of(authorities));
    }

    private static final class StubProvider implements SemanticResourceCountProvider {
        @Override
        public List<ResourceCountDescriptor> descriptors() {
            return List.of(new ResourceCountDescriptor(
                    "usuarios", "Usuarios", "Usuarios cadastrados", "iam:usuario:read"));
        }

        @Override
        public long count(String resourceId, SemanticQueryContext context) {
            if (!context.possuiAutoridade("iam:usuario:read")) {
                throw new AccessDeniedException("Recurso nao autorizado");
            }
            return 42;
        }
    }
}
