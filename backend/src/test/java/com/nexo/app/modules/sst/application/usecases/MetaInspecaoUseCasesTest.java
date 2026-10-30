package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.FuncaoApi;
import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoResponse;
import com.nexo.app.modules.sst.application.mappers.MetaInspecaoMapper;
import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoRepository;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetaInspecaoUseCasesTest {

    private static final UUID TIPO_INSPECAO = UUID.randomUUID();
    private static final UUID FUNCAO = UUID.randomUUID();
    private static final UUID COLABORADOR = UUID.randomUUID();

    @Mock
    private MetaInspecaoRepository repository;
    @Mock
    private TipoInspecaoRepository tipoInspecaoRepository;
    @Mock
    private MetaInspecaoMapper mapper;
    @Mock
    private FuncaoApi funcaoApi;
    @Mock
    private ColaboradorApi colaboradorApi;

    @InjectMocks
    private MetaInspecaoUseCases useCases;

    @BeforeEach
    void configurarPadroes() {
        when(tipoInspecaoRepository.findById(TIPO_INSPECAO))
                .thenReturn(Optional.of(new TipoInspecao("Campo", "Inspeção em campo", 1)));
        when(funcaoApi.existeFuncao(FUNCAO)).thenReturn(true);
        when(funcaoApi.buscarPorIdResponse(FUNCAO))
                .thenReturn(Optional.of(new FuncaoResponse(FUNCAO, "ENCARREGADO", null, true)));
        when(colaboradorApi.existeColaborador(COLABORADOR)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByAtivoTrueAndIdFuncao(any())).thenReturn(List.of());
        when(repository.findByAtivoTrueAndIdColaborador(any())).thenReturn(List.of());
        when(mapper.toResponse(any())).thenAnswer(inv -> respostaDe(inv.getArgument(0)));
    }

    private MetaInspecaoRequest requestDeFuncao() {
        return new MetaInspecaoRequest(TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL,
                LocalDate.now().minusMonths(2).withDayOfMonth(1), null, "Meta mensal dos encarregados");
    }

    private MetaInspecaoResponse respostaDe(MetaInspecao meta) {
        return new MetaInspecaoResponse(meta.getId(), meta.getIdTipoInspecao(), "Campo", meta.getEscopo(), meta.getIdFuncao(), null,
                meta.getIdColaborador(), null, meta.getQuantidade(), meta.getPeriodicidade(),
                meta.getVigenciaInicio(), meta.getVigenciaFim(), meta.getDescricao(), meta.isAtivo(), null, null);
    }

    @Test
    @DisplayName("criar deve persistir a meta da função e devolver a janela vigente")
    void criarDevePersistirMetaDeFuncao() {
        MetaInspecaoResponse response = useCases.criar(requestDeFuncao());

        ArgumentCaptor<MetaInspecao> captor = ArgumentCaptor.forClass(MetaInspecao.class);
        verify(repository).save(captor.capture());

        MetaInspecao salva = captor.getValue();
        assertEquals(TIPO_INSPECAO, salva.getIdTipoInspecao());
        assertEquals(EscopoMeta.FUNCAO, salva.getEscopo());
        assertEquals(FUNCAO, salva.getIdFuncao());
        assertEquals(4, salva.getQuantidade());
        assertEquals(PeriodicidadeMeta.MENSAL, salva.getPeriodicidade());

        assertEquals("ENCARREGADO", response.nomeFuncao());
        assertNotNull(response.janelaAtualInicio(), "a meta vigente deve expor a janela atual");
        assertNotNull(response.janelaAtualFim());
    }

    @Test
    @DisplayName("criar deve rejeitar alvo inexistente ou inativo")
    void criarDeveRejeitarAlvoInexistente() {
        UUID inexistente = UUID.randomUUID();
        when(funcaoApi.existeFuncao(inexistente)).thenReturn(false);

        MetaInspecaoRequest request = new MetaInspecaoRequest(TIPO_INSPECAO, inexistente, null, 4,
                PeriodicidadeMeta.MENSAL, LocalDate.now(), null, null);

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.criar(request));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("criar deve rejeitar vigência sobreposta a outra meta ativa do mesmo alvo")
    void criarDeveRejeitarSobreposicao() {
        MetaInspecao existente = new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 6, PeriodicidadeMeta.MENSAL,
                LocalDate.now().minusYears(1).withDayOfMonth(1), null, null);
        when(repository.findByAtivoTrueAndIdFuncao(FUNCAO)).thenReturn(List.of(existente));

        MetaInspecaoRequest request = requestDeFuncao();

        assertThrows(InvarianteVioladaException.class, () -> useCases.criar(request));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("criar deve aceitar meta individual mesmo existindo meta para a função")
    void criarDeveAceitarMetaIndividual() {
        MetaInspecao daFuncao = new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 6, PeriodicidadeMeta.MENSAL,
                LocalDate.now().minusYears(1).withDayOfMonth(1), null, null);
        when(repository.findByAtivoTrueAndIdFuncao(FUNCAO)).thenReturn(List.of(daFuncao));

        MetaInspecaoRequest request = new MetaInspecaoRequest(TIPO_INSPECAO, null, COLABORADOR, 2,
                PeriodicidadeMeta.MENSAL, LocalDate.now().withDayOfMonth(1), null, "Exceção individual");

        MetaInspecaoResponse response = useCases.criar(request);

        assertEquals(EscopoMeta.COLABORADOR, response.escopo());
        verify(repository).save(any());
    }

    @Test
    @DisplayName("atualizar deve alterar quantidade e vigência preservando o alvo")
    void atualizarDeveAlterarDados() {
        MetaInspecao meta = new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, "Meta antiga");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(meta));

        useCases.atualizar(id, new MetaInspecaoRequest(TIPO_INSPECAO, FUNCAO, null, 8, PeriodicidadeMeta.TRIMESTRAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Meta revisada"));

        assertEquals(8, meta.getQuantidade());
        assertEquals(PeriodicidadeMeta.TRIMESTRAL, meta.getPeriodicidade());
        assertEquals(LocalDate.of(2026, 12, 31), meta.getVigenciaFim());
        assertEquals(FUNCAO, meta.getIdFuncao());
    }

    @Test
    @DisplayName("atualizar deve rejeitar a troca do alvo da meta")
    void atualizarDeveRejeitarTrocaDeAlvo() {
        MetaInspecao meta = new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, null);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(meta));

        MetaInspecaoRequest request = new MetaInspecaoRequest(TIPO_INSPECAO, null, COLABORADOR, 4,
                PeriodicidadeMeta.MENSAL, LocalDate.of(2026, 1, 1), null, null);

        assertThrows(InvarianteVioladaException.class, () -> useCases.atualizar(id, request));
        assertEquals(FUNCAO, meta.getIdFuncao());
    }

    @Test
    @DisplayName("desativar deve aplicar o soft delete da meta")
    void desativarDeveAplicarSoftDelete() {
        MetaInspecao meta = new MetaInspecao(TIPO_INSPECAO, FUNCAO, null, 4, PeriodicidadeMeta.MENSAL,
                LocalDate.of(2026, 1, 1), null, null);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(meta));

        useCases.desativar(id);

        assertFalse(meta.isAtivo());
        verify(repository).save(meta);
    }
}
