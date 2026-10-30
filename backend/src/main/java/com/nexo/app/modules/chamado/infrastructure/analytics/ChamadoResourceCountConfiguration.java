package com.nexo.app.modules.chamado.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class ChamadoResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider chamadoResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                new ResourceDefinition("chamados", "Chamados", "Chamados registrados no Nexo.",
                        "chamado:read", "select count(c.id) from Chamado c")));
    }
}
