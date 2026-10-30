package com.nexo.app.modules.financeiro.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticResourceCountProvider.ResourceDefinition;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class FinanceiroResourceCountConfiguration {

    @Bean
    SemanticResourceCountProvider financeiroResourceCountProvider(EntityManager entityManager) {
        return new JpaSemanticResourceCountProvider(entityManager, List.of(
                resource("solicitacoes_compra", "Solicitacoes de compra", "Solicitacoes de compra registradas.",
                        "financeiro:compra:read", "select count(s.id) from SolicitacaoCompra s"),
                resource("solicitacoes_pagamento", "Solicitacoes de pagamento", "Solicitacoes de pagamento registradas.",
                        "financeiro:pagamento:read", "select count(s.id) from SolicitacaoPagamento s"),
                resource("fornecedores", "Fornecedores", "Fornecedores cadastrados, inclusive desativados.",
                        "financeiro:fornecedor:read", "select count(f.id) from Fornecedor f"),
                resource("formularios_financeiros", "Formularios financeiros", "Formularios de solicitacao financeira cadastrados.",
                        "financeiro:formulario:read", "select count(f.id) from FormularioSolicitacao f"),
                resource("documentos", "Documentos financeiros", "Documentos no repositorio documental do financeiro.",
                        "financeiro:documento:read", "select count(d.id) from DocumentoFinanceiro d")));
    }

    private static ResourceDefinition resource(String id, String nome, String descricao, String authority, String jpql) {
        return new ResourceDefinition(id, nome, descricao, authority, jpql);
    }
}
