package com.nexo.app.modules.assistente.application;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SemanticResourceCountIntegrationTest {

    @Autowired
    private List<SemanticResourceCountProvider> providers;

    @Autowired
    private SemanticResourceCountRegistry registry;

    @Test
    void todosOsRecursosPublicadosPossuemConsultaJpaValida() {
        var descriptors = providers.stream()
                .flatMap(provider -> provider.descriptors().stream())
                .toList();
        Set<String> authorities = descriptors.stream()
                .map(SemanticResourceCountProvider.ResourceCountDescriptor::autoridadeObrigatoria)
                .collect(Collectors.toSet());
        SemanticQueryContext context = new SemanticQueryContext(UUID.randomUUID(), authorities);

        assertThat(descriptors).hasSize(39);
        assertThat(registry.listarAutorizados(context)).hasSize(39);
        assertThat(descriptors).allSatisfy(descriptor ->
                assertThat(registry.contar(descriptor.id(), context)).isNotNegative());
    }
}
