package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.SaldoFilter;
import com.nexo.app.modules.estoque.domain.Saldo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaldoSpecificationTest {

    @Test
    void negativoVerdadeiroFiltraQuantidadeMenorQueZero() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked") Root<Saldo> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        @SuppressWarnings("unchecked") Path<BigDecimal> quantidade = mock(Path.class);
        when(root.<BigDecimal>get("quantidade")).thenReturn(quantidade);
        when(cb.lessThan(quantidade, BigDecimal.ZERO)).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SaldoSpecification.filterBy(filtro(true)).toPredicate(root, query, cb);

        verify(cb).lessThan(quantidade, BigDecimal.ZERO);
    }

    @Test
    void negativoFalsoFiltraQuantidadeNaoNegativa() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked") Root<Saldo> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        @SuppressWarnings("unchecked") Path<BigDecimal> quantidade = mock(Path.class);
        when(root.<BigDecimal>get("quantidade")).thenReturn(quantidade);
        when(cb.greaterThanOrEqualTo(quantidade, BigDecimal.ZERO)).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SaldoSpecification.filterBy(filtro(false)).toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(quantidade, BigDecimal.ZERO);
    }

    private SaldoFilter filtro(Boolean negativo) {
        return new SaldoFilter(null, null, null, null, null, null, null, null, null, negativo);
    }
}
