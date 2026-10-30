package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialLookupProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import com.nexo.app.shared.application.lookup.InvalidLookupCursorException;
import com.nexo.app.shared.application.lookup.LookupCursor;
import com.nexo.app.shared.application.lookup.LookupCursorCodec;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialLookupUseCaseTest {

    @Mock
    private MaterialRepository repository;

    @Mock
    private LookupMetrics metrics;

    @Test
    void queryInsuficienteRetornaVazioSemConsultarRepositorio() {
        MaterialLookupUseCase useCase = new MaterialLookupUseCase(repository, metrics);

        LookupPageDto<LookupOptionDto> result = useCase.execute(" á ", 20, null);

        assertTrue(result.items().isEmpty());
        assertNull(result.nextCursor());
        assertFalse(result.hasMore());
        verifyNoInteractions(repository);
        verify(metrics).recordRequest("material", "/materiais/options", "success");
    }

    @Test
    void buscaNormalizaQueryEBuscaUmaLinhaExtra() {
        MaterialLookupUseCase useCase = new MaterialLookupUseCase(repository, metrics);
        MaterialLookupProjection first = row("ABC", "Água", "agua", UUID.randomUUID());
        MaterialLookupProjection second = row("ABD", "Água mineral", "agua mineral", UUID.randomUUID());
        MaterialLookupProjection extra = row("ABE", "Águas", "aguas", UUID.randomUUID());
        when(repository.buscarOpcoes(eq("agua"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(first, second, extra));

        LookupPageDto<LookupOptionDto> result = useCase.execute(" Água ", 2, null);

        assertEquals(2, result.items().size());
        assertEquals(first.getId().toString(), result.items().getFirst().value());
        assertEquals("ABC — Água", result.items().getFirst().label());
        assertTrue(result.hasMore());
        LookupCursor cursor = LookupCursorCodec.decode(result.nextCursor());
        assertEquals(second.getSortKey(), cursor.sortKey());
        assertEquals(second.getId(), cursor.id());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarOpcoes(eq("agua"), isNull(), isNull(), pageable.capture());
        assertEquals(3, pageable.getValue().getPageSize());
    }

    @Test
    void limitaPedidoAbusivoAoMaximoMaisUmaLinha() {
        MaterialLookupUseCase useCase = new MaterialLookupUseCase(repository, metrics);
        when(repository.buscarOpcoes(eq("agua"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        useCase.execute("agua", 500, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarOpcoes(eq("agua"), isNull(), isNull(), pageable.capture());
        assertEquals(101, pageable.getValue().getPageSize());
    }

    @Test
    void cursorEhRepassadoAoRepositorioEUltimaPaginaNaoGeraProximoCursor() {
        MaterialLookupUseCase useCase = new MaterialLookupUseCase(repository, metrics);
        UUID id = UUID.randomUUID();
        String encoded = LookupCursorCodec.encode("parafuso", id);
        MaterialLookupProjection row = row("MAT-1", "Parafuso", "parafuso", UUID.randomUUID());
        when(repository.buscarOpcoes(eq("par"), eq("parafuso"), eq(id), any(Pageable.class)))
                .thenReturn(List.of(row));

        LookupPageDto<LookupOptionDto> result = useCase.execute("par", 10, encoded);

        assertEquals(1, result.items().size());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
        verify(repository).buscarOpcoes(eq("par"), eq("parafuso"), eq(id), any(Pageable.class));
    }

    @Test
    void cursorInvalidoEhRejeitado() {
        MaterialLookupUseCase useCase = new MaterialLookupUseCase(repository, metrics);

        assertThrows(InvalidLookupCursorException.class,
                () -> useCase.execute("par", 10, "cursor-invalido"));
        verifyNoInteractions(repository);
    }

    private MaterialLookupProjection row(String codigo, String descricao, String sortKey, UUID id) {
        MaterialLookupProjection row = mock(MaterialLookupProjection.class);
        lenient().when(row.getId()).thenReturn(id);
        lenient().when(row.getCodigo()).thenReturn(codigo);
        lenient().when(row.getDescricao()).thenReturn(descricao);
        lenient().when(row.getSortKey()).thenReturn(sortKey);
        return row;
    }
}
