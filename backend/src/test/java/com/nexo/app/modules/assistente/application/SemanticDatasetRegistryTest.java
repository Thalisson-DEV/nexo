package com.nexo.app.modules.assistente.application;

import com.nexo.app.shared.application.analytics.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticDatasetRegistryTest {

    @Test
    void catalogoOmiteDatasetNaoAutorizadoEExecucaoConfereNovamente() {
        SemanticDatasetProvider provider = new StubProvider();
        SemanticDatasetRegistry registry = new SemanticDatasetRegistry(List.of(provider));
        SemanticQueryContext denied = new SemanticQueryContext(UUID.randomUUID(), Set.of());
        SemanticQuery query = new SemanticQuery("sst_teste", List.of("status"), List.of(),
                List.of(), List.of(), List.of(), 10);

        assertThat(registry.listarAutorizados(denied)).isEmpty();
        assertThatThrownBy(() -> registry.executar(query, denied)).isInstanceOf(AccessDeniedException.class);
    }

    private static class StubProvider implements SemanticDatasetProvider {
        @Override
        public SemanticDatasetDescriptor descriptor() {
            return new SemanticDatasetDescriptor("sst_teste", "1", "SST", "SST", "sst:read", true,
                    10, 30, List.of(new SemanticDatasetDescriptor.SemanticDimension(
                    "status", "Status", SemanticDataType.TEXT, true, false, Set.of(SemanticFilterOperator.EQ))),
                    List.of(), List.of());
        }

        @Override
        public SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context) {
            return new SemanticQueryResult("sst_teste", "1", List.of(), 0, false,
                    "America/Sao_Paulo", Map.of(), OffsetDateTime.now());
        }
    }
}
