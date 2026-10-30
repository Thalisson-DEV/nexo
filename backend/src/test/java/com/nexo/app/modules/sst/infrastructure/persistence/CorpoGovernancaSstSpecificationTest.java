package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstFilter;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorpoGovernancaSstSpecificationTest {

    private Root<CorpoGovernancaSst> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> path;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void configurar() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        path = mock(Path.class);

        when(root.get(anyString())).thenReturn(path);
        when(cb.isTrue(any())).thenReturn(mock(Predicate.class));
        when(path.in(anyCollection())).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
    }

    @Test
    void listasVaziasSignificamAusenciaDeFiltro() {
        CorpoGovernancaSstFilter filter = new CorpoGovernancaSstFilter(
                List.of(TipoCorpoGovernancaSst.CIPA), null, null, null,
                null, null, null, null, null);

        CorpoGovernancaSstSpecification.filterBy(filter).toPredicate(root, query, cb);

        verify(root).get("ativo");
        verify(root).get("tipo");
        verify(path).in(List.of(TipoCorpoGovernancaSst.CIPA));
        verify(root, never()).get("status");
        verify(root, never()).get("regimeComposicao");
        verify(root, never()).get("idBaseOperacional");
    }
}
