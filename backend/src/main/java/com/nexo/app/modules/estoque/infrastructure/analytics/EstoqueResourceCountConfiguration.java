package com.nexo.app.modules.estoque.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class EstoqueResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider estoqueResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("materiais", "Materiais", "Materiais cadastrados, inclusive desativados.",
                        "estoque:material:read", "select count(m.id) from Material m"),
                resource("estoques", "Estoques", "Estoques cadastrados, inclusive desativados.",
                        "estoque:estoque:read", "select count(e.id) from Estoque e"),
                resource("tipos_estoque", "Tipos de estoque", "Tipos de estoque cadastrados.",
                        "estoque:tipo-estoque:read", "select count(t.id) from TipoEstoque t"),
                resource("movimentacoes_estoque", "Movimentacoes de estoque", "Movimentacoes de estoque registradas.",
                        "estoque:movimentacao:read", "select count(m.id) from MovimentacaoEstoque m"),
                resource("solicitacoes_material", "Solicitacoes de material", "Solicitacoes de material registradas.",
                        "estoque:solicitacao:read", "select count(s.id) from SolicitacaoMaterial s"),
                resource("centros_investimento", "Centros de investimento", "Centros de investimento cadastrados.",
                        "estoque:centro-investimento:read", "select count(c.id) from CentroInvestimento c"),
                resource("avaliacoes_material", "Avaliacoes de material", "Avaliacoes de material registradas.",
                        "estoque:avaliacao-material:read", "select count(a.id) from AvaliacaoMaterial a")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
