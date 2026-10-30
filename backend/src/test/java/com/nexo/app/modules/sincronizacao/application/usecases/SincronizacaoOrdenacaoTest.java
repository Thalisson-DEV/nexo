package com.nexo.app.modules.sincronizacao.application.usecases;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class SincronizacaoOrdenacaoTest {

    @Test
    void translatesReceivedAtAndTypeIntoMappedEntityAttributes() {
        var pageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.asc("receivedAt"), Sort.Order.desc("tipo")));

        var normalized = SincronizacaoUseCases.normalizarOrdenacao(pageable);

        assertThat(normalized.getSort().getOrderFor("dataCriacao").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(normalized.getSort().getOrderFor("operacao.tipo").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(normalized.getSort().getOrderFor("receivedAt")).isNull();
    }

    @Test
    void ignoresUnknownClientSortProperties() {
        var normalized = SincronizacaoUseCases.normalizarOrdenacao(
                PageRequest.of(0, 20, Sort.by("nonexistentAttribute")));

        assertThat(normalized.getSort().getOrderFor("nonexistentAttribute")).isNull();
        assertThat(normalized.getSort().getOrderFor("dataCriacao")).isNotNull();
    }
}
