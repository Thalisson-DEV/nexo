package com.nexo.app.modules.iam.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class IamResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider iamResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("usuarios", "Usuarios", "Usuarios cadastrados no Nexo, inclusive desativados.",
                        "iam:usuario:read", "select count(u.id) from Usuario u"),
                resource("usuarios_ativos", "Usuarios ativos", "Usuarios com acesso ativo ao Nexo.",
                        "iam:usuario:read", "select count(u.id) from Usuario u where u.ativo = true"),
                resource("perfis_acesso", "Perfis de acesso", "Perfis de acesso cadastrados, inclusive desativados.",
                        "iam:perfil:read", "select count(p.id) from PerfilAcesso p"),
                resource("permissoes", "Permissoes", "Permissoes funcionais cadastradas, inclusive desativadas.",
                        "iam:permissao:read", "select count(p.id) from Permissao p")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
