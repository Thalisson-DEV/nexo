package com.nexo.app.modules.contrato.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class ContratoResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider contratoResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("contratos", "Contratos", "Contratos cadastrados, inclusive desativados.",
                        "contrato:contrato:read", "select count(c.id) from Contrato c"),
                resource("acoes_contrato", "Acoes de contrato", "Acoes operacionais vinculadas aos contratos.",
                        "contrato:acao:read", "select count(a.id) from Acao a"),
                resource("valores_acoes_contrato", "Valores de acoes", "Registros de valores das acoes contratuais.",
                        "contrato:acao-valor:read", "select count(v.id) from AcaoValor v")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
