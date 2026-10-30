package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.TipoInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoResponse;
import com.nexo.app.modules.sst.application.mappers.TipoInspecaoMapper;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TipoInspecaoUseCasesTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private TipoInspecaoRepository repository;
    @Mock
    private TipoInspecaoMapper mapper;

    @InjectMocks
    private TipoInspecaoUseCases useCases;

    @BeforeEach
    void configurarPadroes() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> respostaDe(inv.getArgument(0)));
    }

    private TipoInspecaoResponse respostaDe(TipoInspecao tipo) {
        return new TipoInspecaoResponse(tipo.getId(), tipo.getNome(), tipo.getDescricao(),
                tipo.getOrdem(), tipo.isAtivo(), tipo.getDataCriacao(), tipo.getDataUltimaAlteracao());
    }

    @Test
    @DisplayName("Deve criar tipo de inspeção com sucesso")
    void deveCriarComSucesso() {
        when(repository.findByNomeNormalizadoAndAtivoTrue("campo")).thenReturn(Optional.empty());

        TipoInspecaoRequest request = new TipoInspecaoRequest("Campo", "Inspeções de campo", 1);
        TipoInspecaoResponse response = useCases.criar(request);

        assertNotNull(response);
        assertEquals("Campo", response.nome());
        verify(repository).save(any(TipoInspecao.class));
    }

    @Test
    @DisplayName("Deve impedir criação com nome duplicado")
    void deveImpedirDuplicidade() {
        TipoInspecao existente = new TipoInspecao("Campo", "Existente", 1);
        when(repository.findByNomeNormalizadoAndAtivoTrue("campo")).thenReturn(Optional.of(existente));

        TipoInspecaoRequest request = new TipoInspecaoRequest("campo", "Tentativa", 1);
        assertThrows(InvarianteVioladaException.class, () -> useCases.criar(request));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve buscar por ID com sucesso")
    void deveBuscarPorId() {
        TipoInspecao tipo = new TipoInspecao("Alojamento", "Desc", 1);
        when(repository.findById(ID)).thenReturn(Optional.of(tipo));

        TipoInspecaoResponse response = useCases.buscarPorId(ID);
        assertNotNull(response);
        assertEquals("Alojamento", response.nome());
    }

    @Test
    @DisplayName("Deve lançar erro ao buscar ID inexistente")
    void deveLancarErroAoBuscarIdInexistente() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.buscarPorId(ID));
    }

    @Test
    @DisplayName("Deve listar ativos ordenados")
    void deveListarAtivos() {
        TipoInspecao t1 = new TipoInspecao("Alojamento", "Desc 1", 1);
        TipoInspecao t2 = new TipoInspecao("Campo", "Desc 2", 2);
        when(repository.listarAtivosOrdenados()).thenReturn(List.of(t1, t2));

        List<TipoInspecaoResponse> lista = useCases.listarAtivos();
        assertEquals(2, lista.size());
        assertEquals("Alojamento", lista.get(0).nome());
        assertEquals("Campo", lista.get(1).nome());
    }

    @Test
    @DisplayName("Deve alternar status de ativo para inativo")
    void deveAlternarStatus() {
        TipoInspecao tipo = new TipoInspecao("Câmera", "Desc", 3);
        assertTrue(tipo.isAtivo());
        when(repository.findById(ID)).thenReturn(Optional.of(tipo));

        useCases.alternarStatus(ID);
        assertFalse(tipo.isAtivo());
        verify(repository).save(tipo);
    }
}
