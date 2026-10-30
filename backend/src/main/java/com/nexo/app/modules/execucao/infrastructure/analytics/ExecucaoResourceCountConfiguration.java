package com.nexo.app.modules.execucao.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class ExecucaoResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider execucaoResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                new ResourceDefinition("execucoes_comerciais", "Execucoes comerciais",
                        "Execucoes comerciais registradas no Nexo.", "execucao:comercial:read",
                        "select count(e.id) from ExecucaoComercial e"),
                new ResourceDefinition("tipos_servico", "Tipos de servico",
                        "Tipos de servico das notas executadas em campo.", "execucao:tipo-servico:read",
                        "select count(t.id) from TipoServico t")));
    }
}
