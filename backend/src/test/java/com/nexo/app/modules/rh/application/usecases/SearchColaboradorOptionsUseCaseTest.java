package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorLookupProjection;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.shared.application.dto.LookupPageDto;
import com.nexo.app.shared.application.lookup.InvalidLookupCursorException;
import com.nexo.app.shared.application.lookup.LookupCursorCodec;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SearchColaboradorOptionsUseCaseTest {

    @Mock
    private ColaboradorRepository repository;

    @Mock
    private LookupMetrics metrics;

    @InjectMocks
    private SearchColaboradorOptionsUseCase useCase;

    @org.junit.jupiter.api.BeforeEach
    void executeObservedQueries() {
        org.mockito.Mockito.lenient().doAnswer(
                        invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get())
                .when(metrics).observeQuery(any(), any(), any(), any());
    }

    @Test
    void naoConsultaRepositorioQuandoQueryTemMenosDeDoisCaracteres() {
        LookupPageDto<?> page = useCase.execute("a", 50, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        verify(repository, never()).buscarOpcoes(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(repository, never()).buscarOpcoesAposCursor(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void normalizaQueryMapaeClampLimiteEMantemLabelsDuplicados() {
        UUID primeiroId = UUID.randomUUID();
        UUID segundoId = UUID.randomUUID();
        List<ColaboradorLookupProjection> rows = List.of(
                row(primeiroId, "João da Silva", "joao da silva"),
                row(segundoId, "João da Silva", "joao da silva"));
        when(repository.buscarOpcoes(eq("joao"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(rows);

        LookupPageDto<?> page = useCase.execute("  JõÃO ", 999, null);

        assertThat(page.items()).extracting("value")
                .containsExactly(primeiroId.toString(), segundoId.toString());
        assertThat(page.items()).extracting("label")
                .containsExactly("João da Silva", "João da Silva");
        assertThat(page.hasMore()).isFalse();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarOpcoes(eq("joao"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(101);
    }

    @Test
    void buscaPaginaSeguintePorCursorOpacoECalculaHasMoreComLimitMaisUm() {
        UUID cursorId = UUID.randomUUID();
        UUID primeiroId = UUID.randomUUID();
        UUID segundoId = UUID.randomUUID();
        String cursor = LookupCursorCodec.encode("maria", cursorId);
        List<ColaboradorLookupProjection> rows = new ArrayList<>();
        rows.add(row(primeiroId, "Maria Um", "maria um"));
        rows.add(row(segundoId, "Maria Dois", "maria dois"));
        when(repository.buscarOpcoesAposCursor(eq("maria"), eq("maria"), eq(cursorId),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(rows);

        LookupPageDto<?> page = useCase.execute("Maria", 1, cursor);

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        verify(repository).buscarOpcoesAposCursor(eq("maria"), eq("maria"), eq(cursorId),
                org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() == 2));
    }

    @Test
    void rejeitaCursorInvalidoAntesDeConsultarRepositorio() {
        assertThatThrownBy(() -> useCase.execute("ab", 50, "corrompido"))
                .isInstanceOf(InvalidLookupCursorException.class);
        verify(repository, never()).buscarOpcoesAposCursor(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static ColaboradorLookupProjection row(UUID id, String label, String searchKey) {
        ColaboradorLookupProjection row = org.mockito.Mockito.mock(ColaboradorLookupProjection.class);
        org.mockito.Mockito.lenient().when(row.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(row.getNomeCompleto()).thenReturn(label);
        org.mockito.Mockito.lenient().when(row.getNomeBusca()).thenReturn(searchKey);
        return row;
    }
}
