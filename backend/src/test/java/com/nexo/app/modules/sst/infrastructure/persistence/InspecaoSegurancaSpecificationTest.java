package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspecaoSegurancaSpecificationTest {

    @SuppressWarnings("unchecked")
    @Test
    void filtroNuloAindaExcluiInspecoesDesativadas() {
        Root<InspecaoSeguranca> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Boolean> ativo = mock(Path.class);
        Predicate ativoPredicate = mock(Predicate.class);

        when(root.<Boolean>get("ativo")).thenReturn(ativo);
        when(cb.isTrue(ativo)).thenReturn(ativoPredicate);

        InspecaoSegurancaSpecification.filterBy(null).toPredicate(root, query, cb);

        verify(cb).and(ativoPredicate);
    }
}
