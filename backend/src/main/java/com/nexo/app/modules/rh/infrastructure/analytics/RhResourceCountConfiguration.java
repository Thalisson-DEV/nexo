package com.nexo.app.modules.rh.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class RhResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider rhResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("colaboradores", "Colaboradores", "Colaboradores cadastrados, inclusive desativados.",
                        "rh:colaborador:read", "select count(c.id) from Colaborador c"),
                resource("colaboradores_ativos", "Colaboradores ativos", "Colaboradores atualmente ativos.",
                        "rh:colaborador:read", "select count(c.id) from Colaborador c where c.ativo = true"),
                resource("equipes", "Equipes", "Equipes cadastradas, inclusive desativadas.",
                        "rh:equipe:read", "select count(e.id) from Equipe e"),
                resource("veiculos", "Veiculos", "Veiculos cadastrados, inclusive desativados.",
                        "rh:veiculo:read", "select count(v.id) from Veiculo v"),
                resource("bases_operacionais", "Bases operacionais", "Bases operacionais cadastradas.",
                        "rh:base-operacional:read", "select count(b.id) from BaseOperacional b"),
                resource("funcoes", "Funcoes", "Funcoes profissionais cadastradas.",
                        "rh:funcao:read", "select count(f.id) from Funcao f"),
                resource("tipos_equipe", "Tipos de equipe", "Tipos de equipe cadastrados.",
                        "rh:tipo-equipe:read", "select count(t.id) from TipoEquipe t"),
                resource("processos_negocio", "Processos de negocio", "Processos de negocio cadastrados.",
                        "rh:processo-negocio:read", "select count(p.id) from ProcessoNegocio p"),
                resource("municipios", "Municipios", "Municipios cadastrados no Nexo.",
                        "rh:municipio:read", "select count(m.id) from Municipio m"),
                resource("centros_faturamento", "Centros de faturamento", "Centros de faturamento cadastrados.",
                        "rh:centro-faturamento:read", "select count(c.id) from CentroFaturamento c")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
