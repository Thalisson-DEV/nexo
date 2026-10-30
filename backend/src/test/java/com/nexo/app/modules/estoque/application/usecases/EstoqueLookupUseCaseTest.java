package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueLookupProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueLookupUseCaseTest {

    @Mock private EstoqueRepository repository;
    private EstoqueLookupUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EstoqueLookupUseCase(repository, new LookupMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void consultaCurtaNaoAcessaRepositorio() {
        assertThat(useCase.execute("a", 20, null, List.of("OBRA")).items()).isEmpty();
        verify(repository, never()).buscarOpcoes(any(), any(), any(), any(), any());
    }

    @Test
    void normalizaTiposEEntregaCursorQuandoHaProximaPagina() {
        EstoqueLookupProjection primeiro = projection("ÁLMOX Central", "almox central");
        EstoqueLookupProjection segundo = projection("Obra Norte", "obra norte");
        when(repository.buscarOpcoes(eq("almox"), eq(List.of("FISICO", "OBRA")),
                eq(null), eq(null), any(Pageable.class)))
                .thenReturn(List.of(primeiro, segundo));

        var page = useCase.execute("  ÁLMOX ", 1, null, List.of("obra,fisico"));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().label()).isEqualTo("ÁLMOX Central");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void rejeitaTipoForaDaAllowlist() {
        assertThatThrownBy(() -> useCase.execute("central", 20, null, List.of("COMERCIAL")))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("nao suportado");
    }

    private EstoqueLookupProjection projection(String nome, String sortKey) {
        UUID id = UUID.randomUUID();
        return new EstoqueLookupProjection() {
            public UUID getId() { return id; }
            public String getNomeIdentificador() { return nome; }
            public String getSortKey() { return sortKey; }
        };
    }
}
