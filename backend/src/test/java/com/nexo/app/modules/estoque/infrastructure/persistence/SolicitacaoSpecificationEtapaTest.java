package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.domain.EtapaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

class SolicitacaoSpecificationEtapaTest {

    private Root<SolicitacaoMaterial> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> path;
    private Join<SolicitacaoMaterial, ItemSolicitacao> itens;
    private Predicate predicate;
    private Expression<BigDecimal> expression;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void configurar() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        path = mock(Path.class);
        itens = mock(Join.class);
        predicate = mock(Predicate.class);
        expression = mock(Expression.class);

        when(root.get(anyString())).thenReturn(path);
        doReturn(itens).when(root).join("itens");
        when(itens.get(anyString())).thenReturn(path);
        when(path.in(anyCollection())).thenReturn(predicate);
        when(path.in(any(Object[].class))).thenReturn(predicate);
        when(predicate.not()).thenReturn(predicate);
        when(cb.not(any())).thenReturn(predicate);
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.isFalse(any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        when(cb.or(any(Predicate[].class))).thenReturn(predicate);
        when(cb.coalesce(any(Expression.class), eq(BigDecimal.ZERO))).thenReturn(expression);
        when(cb.sum(any(Expression.class), any(Expression.class))).thenReturn(expression);
        when(cb.diff(any(Expression.class), any(Expression.class))).thenReturn(expression);
        when(cb.greaterThan(any(Expression.class), eq(BigDecimal.ZERO))).thenReturn(predicate);
        doReturn(query).when(query).distinct(true);
    }

    @Test
    void aguardandoPlanejamentoRestringeAoContextoTecnicoAbertoEComPendencia() {
        SolicitacaoSpecification.filterBy(filtro(EtapaSolicitacaoTecnica.AGUARDANDO_PLANEJAMENTO))
                .toPredicate(root, query, cb);

        verify(root).get("contexto");
        verify(root).get("tipoTecnico");
        verify(root).get("status");
        verify(root).get("encerradaEm");
        verify(root).get("dataPrevistaRetirada");
        verify(root, atLeastOnce()).get("statusSeparacao");
    }

    @Test
    void emSeparacaoExigeAdicaoTecnicaAberta() {
        SolicitacaoSpecification.filterBy(filtro(EtapaSolicitacaoTecnica.EM_SEPARACAO))
                .toPredicate(root, query, cb);

        verify(root).get("contexto");
        verify(root).get("tipoTecnico");
        verify(root, atLeastOnce()).get("statusSeparacao");
        verify(root).get("encerradaEm");
    }

    @Test
    void pendenteEntregaUsaQuantidadeNoBancoEDeduplicaItens() {
        SolicitacaoSpecification.filterBy(filtro(EtapaSolicitacaoTecnica.PENDENTE_ENTREGA))
                .toPredicate(root, query, cb);

        verify(root).join("itens");
        verify(query).distinct(true);
        verify(itens, atLeastOnce()).get("quantidade");
        verify(itens).get("quantidadeEntregueBruta");
        verify(itens).get("quantidadeEntregaEstornada");
        verify(itens).get("quantidadeDevolvidaBruta");
        verify(itens).get("quantidadeDevolucaoEstornada");
        verify(cb, atLeastOnce()).greaterThan(any(Expression.class), eq(BigDecimal.ZERO));
    }

    private SolicitacaoFilter filtro(EtapaSolicitacaoTecnica etapa) {
        return new SolicitacaoFilter(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, etapa, null, null, null, null, null);
    }
}
