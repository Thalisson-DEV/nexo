package com.nexo.app.shared.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaSemanticResourceCountProviderTest {

    @Test
    void executaSomenteJpqlPreRegistradoDepoisDaAutorizacao() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(entityManager.createQuery("select count(u.id) from Usuario u", Long.class)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(9L);
        var provider = new JpaSemanticResourceCountProvider(entityManager, List.of(
                new ResourceDefinition("usuarios", "Usuarios", "Usuarios cadastrados",
                        "iam:usuario:read", "select count(u.id) from Usuario u")));

        long total = provider.count("usuarios", context("iam:usuario:read"));

        assertThat(total).isEqualTo(9);
        verify(entityManager).createQuery("select count(u.id) from Usuario u", Long.class);
    }

    @Test
    void bloqueiaAntesDeConsultarBancoQuandoUsuarioNaoTemPermissao() {
        EntityManager entityManager = mock(EntityManager.class);
        var provider = new JpaSemanticResourceCountProvider(entityManager, List.of(
                new ResourceDefinition("usuarios", "Usuarios", "Usuarios cadastrados",
                        "iam:usuario:read", "select count(u.id) from Usuario u")));

        assertThatThrownBy(() -> provider.count("usuarios", context()))
                .isInstanceOf(AccessDeniedException.class);
        verify(entityManager, org.mockito.Mockito.never())
                .createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Long.class));
    }

    private static SemanticQueryContext context(String... authorities) {
        return new SemanticQueryContext(UUID.randomUUID(), Set.of(authorities));
    }
}
