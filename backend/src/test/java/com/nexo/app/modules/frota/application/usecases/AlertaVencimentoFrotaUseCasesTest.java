package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.domain.AlertaVencimento;
import com.nexo.app.modules.frota.domain.DocumentoFrota;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.ParametrosFrota;
import com.nexo.app.modules.frota.domain.TipoAlvoAlerta;
import com.nexo.app.modules.frota.domain.TipoDocumentoFrota;
import com.nexo.app.modules.frota.infrastructure.persistence.AlertaVencimentoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.CondutorRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.DocumentoFrotaRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.ImplementoRepository;
import com.nexo.app.shared.domain.events.FrotaVencimentoProximoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaVencimentoFrotaUseCasesTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 9);

    @Mock
    private AlertaVencimentoRepository alertaRepository;
    @Mock
    private DocumentoFrotaRepository documentoRepository;
    @Mock
    private CondutorRepository condutorRepository;
    @Mock
    private FichaVeiculoRepository fichaRepository;
    @Mock
    private ImplementoRepository implementoRepository;
    @Mock
    private ParametrosFrotaUseCases parametros;
    @Mock
    private ReferenciasFrota referencias;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private AlertaVencimentoFrotaUseCases useCases;

    private final UUID idVeiculo = UUID.randomUUID();
    private DocumentoFrota crlv;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = ParametrosFrota.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        ParametrosFrota valores = ctor.newInstance();
        valores.atualizar(List.of(30, 15, 7, 0), 1500);
        when(parametros.obter()).thenReturn(valores);
        crlv = DocumentoFrota.doVeiculo(idVeiculo, new DocumentoFrota.DadosDocumento(
                TipoDocumentoFrota.CRLV, null, "123", null, HOJE.plusDays(5), null));
        ReflectionTestUtils.setField(crlv, "id", UUID.randomUUID());
        when(documentoRepository.findAllByAtivoTrueAndDataVencimentoLessThanEqual(HOJE.plusDays(30)))
                .thenReturn(List.of(crlv));
        lenient().when(fichaRepository.findAllByIdVeiculoIn(anyCollection()))
                .thenReturn(List.of(FichaVeiculo.provisionar(idVeiculo, "ABC1D23", null)));
        lenient().when(implementoRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(condutorRepository.findAllByAtivoTrueAndCnhValidadeLessThanEqual(any())).thenReturn(List.of());
    }

    @Test
    void publicaUmAvisoNaFaixaMaisUrgenteAlcancada() {
        when(alertaRepository.existsByTipoAlvoAndIdAlvoAndDataVencimentoAndDiasAntecedenciaLessThanEqual(
                TipoAlvoAlerta.DOCUMENTO, crlv.getId(), crlv.getDataVencimento(), 7)).thenReturn(false);

        int avisos = useCases.processar(HOJE);

        assertThat(avisos).isEqualTo(1);
        var alerta = ArgumentCaptor.forClass(AlertaVencimento.class);
        verify(alertaRepository).save(alerta.capture());
        assertThat(alerta.getValue().getDiasAntecedencia()).isEqualTo(7);
        var evento = ArgumentCaptor.forClass(FrotaVencimentoProximoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().referencia()).isEqualTo("ABC1D23");
        assertThat(evento.getValue().prazo()).isEqualTo("vence em 5 dias");
        assertThat(evento.getValue().link()).isEqualTo("/frota/veiculos/" + idVeiculo);
    }

    @Test
    void naoRepeteAvisoJaEmitido() {
        when(alertaRepository.existsByTipoAlvoAndIdAlvoAndDataVencimentoAndDiasAntecedenciaLessThanEqual(
                any(), any(), any(), anyInt())).thenReturn(true);

        assertThat(useCases.processar(HOJE)).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
