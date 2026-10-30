package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.IdsBuscaSolicitacaoFinanceira;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class SolicitacaoFinanceiraSpecificationQueryTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void constroiBuscaGlobalDeCompraComCamposTipados() {
        var criteria = entityManager.getCriteriaBuilder().createQuery(SolicitacaoCompra.class);
        var root = criteria.from(SolicitacaoCompra.class);
        var nucleo = root.get("nucleo");
        var campos = java.util.List.<Expression<String>>of(nucleo.get("descricao"));
        var predicate = BuscaRapidaSolicitacaoFinanceira.montar(
                "2026-08-15", root, nucleo, criteria, entityManager.getCriteriaBuilder(), campos,
                IdsBuscaSolicitacaoFinanceira.vazio(), false);
        criteria.select(root).where(predicate);

        assertThatCode(() -> entityManager.createQuery(criteria)).doesNotThrowAnyException();
    }

    @Test
    void constroiBuscaGlobalDePagamentoIncluindoCompraEAnexos() {
        var criteria = entityManager.getCriteriaBuilder().createQuery(SolicitacaoPagamento.class);
        var root = criteria.from(SolicitacaoPagamento.class);
        var nucleo = root.get("nucleo");
        var campos = java.util.List.<Expression<String>>of(nucleo.get("descricao"));
        var predicate = BuscaRapidaSolicitacaoFinanceira.montar(
                "aprovado", root, nucleo, criteria, entityManager.getCriteriaBuilder(), campos,
                IdsBuscaSolicitacaoFinanceira.vazio(), true);
        criteria.select(root).where(predicate);

        assertThatCode(() -> entityManager.createQuery(criteria)).doesNotThrowAnyException();
    }
}
