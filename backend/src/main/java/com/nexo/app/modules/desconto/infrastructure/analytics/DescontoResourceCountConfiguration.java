package com.nexo.app.modules.desconto.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class DescontoResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider descontoResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("descontos", "Descontos", "Solicitacoes de desconto registradas.",
                        "desconto:read", "select count(d.id) from Desconto d"),
                resource("motivos_desconto", "Motivos de desconto", "Motivos de desconto cadastrados.",
                        "desconto:motivo:read", "select count(m.id) from MotivoDesconto m"),
                resource("tipos_equipamento_desconto", "Tipos de equipamento para desconto", "Tipos de equipamento usados em descontos.",
                        "desconto:tipo-equipamento:read", "select count(t.id) from TipoEquipamento t")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
