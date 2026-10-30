package com.nexo.app.modules.sincronizacao.application.usecases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.nexo.app.modules.sincronizacao.domain.TipoOperacaoSincronizacao;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SincronizacaoListagemIntegrationTest {
    @Autowired SincronizacaoUseCases useCases;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void createH2FallbackForPostgresJsonbTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS operacoes_sincronizacao (id UUID PRIMARY KEY, tipo VARCHAR(30), id_operacao_local UUID)");
    }

    @Test
    void acceptsFrontendSortAndDateFiltersInRepositoryQuery() {
        var result = useCases.listar(
                PageRequest.of(0, 20, Sort.by("receivedAt").ascending()),
                null, null, null, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25));
        assertThat(result.getContent()).isEmpty();
        assertThat(useCases.listar(PageRequest.of(0, 20, Sort.by("tipo")),
                null, TipoOperacaoSincronizacao.TURNO, "falha", null, null).getContent()).isEmpty();
        assertThat(useCases.listar(PageRequest.of(0, 20, Sort.by("receivedAt")),
                null, null, "e75eac08-c146-4b28-a42e-08f0cfb40e9d", null, null).getContent()).isEmpty();
        assertThat(useCases.listar(PageRequest.of(0, 20, Sort.by("receivedAt")),
                null, null, "turno", null, null).getContent()).isEmpty();
    }
}
