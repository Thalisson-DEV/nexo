package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.sst.application.dto.MeuDesvioFilter;
import com.nexo.app.modules.sst.application.dto.TratarDesviosEmMassaRequest;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeuDesvioUseCasesTest {

    @Mock private DesvioSegurancaRepository repository;
    @Mock private FormularioInspecaoRepository formularioRepository;
    @Mock private ProcessoNegocioApi processoApi;
    @Mock private BaseOperacionalApi baseOperacionalApi;
    @Mock private EquipeApi equipeApi;
    @Mock private ColaboradorApi colaboradorApi;

    @InjectMocks
    private MeuDesvioUseCases useCases;

    private final UUID idUsuario = UUID.randomUUID();
    private final UUID idColaborador = UUID.randomUUID();

    @Test
    void deveTratarEmMassaSomenteDesviosDasInspecoesDoInspetorLogado() {
        DesvioSeguranca primeiro = novoDesvio();
        DesvioSeguranca segundo = novoDesvio();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(primeiro, segundo));

        var response = useCases.tratarEmMassa(idUsuario, new TratarDesviosEmMassaRequest(
                List.of(primeiro.getId(), segundo.getId()),
                StatusDesvio.EM_TRATAMENTO, "Corrigir imediatamente", null));

        assertEquals(2, response.totalAtualizados());
        assertEquals(StatusDesvio.EM_TRATAMENTO, primeiro.getStatus());
        assertEquals("Corrigir imediatamente", segundo.getAcaoCorretiva());
        verify(repository).saveAll(List.of(primeiro, segundo));
    }

    @Test
    void deveAbortarLoteQuandoAlgumDesvioNaoPertenceAoUsuario() {
        DesvioSeguranca encontrado = novoDesvio();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(encontrado));

        var request = new TratarDesviosEmMassaRequest(
                List.of(encontrado.getId(), UUID.randomUUID()),
                StatusDesvio.RESOLVIDO, "Concluído", null);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.tratarEmMassa(idUsuario, request));
        assertEquals(StatusDesvio.ABERTO, encontrado.getStatus());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveListarSemExigirVinculoComColaborador() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var response = useCases.listar(idUsuario, new MeuDesvioFilter(
                List.of(StatusDesvio.ABERTO), null, null, null,
                null, null, null, null, null, null, null), pageable);

        assertTrue(response.getContent().isEmpty());
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    private DesvioSeguranca novoDesvio() {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), idUsuario,
                null, null, UUID.randomUUID(), OffsetDateTime.now(), null);
        inspecao.adicionarColaboradorInspecionado(idColaborador);
        DesvioSeguranca desvio = inspecao.registrarDesvio(
                UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.MEDIA,
                "Desvio", null, null, List.of(idColaborador));
        ReflectionTestUtils.setField(inspecao, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(desvio, "id", UUID.randomUUID());
        return desvio;
    }
}
