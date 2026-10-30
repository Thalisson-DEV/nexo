package com.nexo.app.modules.sst.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class SstResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider sstResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("inspecoes_seguranca", "Inspecoes de seguranca", "Inspecoes de seguranca registradas.",
                        "sst:inspecao:read", "select count(i.id) from InspecaoSeguranca i"),
                resource("desvios_seguranca", "Desvios de seguranca", "Desvios de seguranca registrados.",
                        "sst:inspecao:read", "select count(d.id) from DesvioSeguranca d"),
                resource("formularios_inspecao", "Formularios de inspecao", "Formularios de inspecao cadastrados.",
                        "sst:formulario:read", "select count(f.id) from FormularioInspecao f"),
                resource("eventos_sst", "Eventos de seguranca",
                        "Acidentes, quase-acidentes, incidentes, danos materiais e doencas ocupacionais registrados.",
                        "sst:evento:read", "select count(e.id) from EventoSst e")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
