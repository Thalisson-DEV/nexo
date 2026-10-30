package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.FrotaOdometroApi;
import com.nexo.app.modules.frota.application.dto.RegistrarLeituraRequest;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.LeituraOdometro;
import com.nexo.app.modules.frota.domain.OrigemLeituraOdometro;
import com.nexo.app.modules.frota.domain.ParametrosFrota;
import com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.LeituraOdometroRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OdometroUseCasesTest {

    @Mock
    private LeituraOdometroRepository repository;
    @Mock
    private FichaVeiculoRepository fichaRepository;
    @Mock
    private ParametrosFrotaUseCases parametros;
    @Mock
    private ReferenciasFrota referencias;
    @InjectMocks
    private OdometroUseCases useCases;

    private final UUID idVeiculo = UUID.randomUUID();
    private LeituraOdometro anterior;

    @BeforeEach
    void setUp() throws Exception {
        var ficha = FichaVeiculo.provisionar(idVeiculo, "ABC1D23", null);
        lenient().when(fichaRepository.findByIdVeiculo(idVeiculo)).thenReturn(Optional.of(ficha));
        lenient().when(fichaRepository.findAllByIdVeiculoIn(anyCollection())).thenReturn(List.of(ficha));
        var ctor = ParametrosFrota.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        ParametrosFrota valores = ctor.newInstance();
        valores.atualizar(List.of(30, 15, 7, 0), 1500);
        lenient().when(parametros.obter()).thenReturn(valores);
        lenient().when(repository.save(any(LeituraOdometro.class))).thenAnswer(i -> {
            LeituraOdometro l = i.getArgument(0);
            ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
            return l;
        });
        anterior = LeituraOdometro.registrar(idVeiculo, 50_000, OffsetDateTime.now().minusDays(1),
                OrigemLeituraOdometro.MANUAL, null, null, null, null, 1500);
        lenient().when(repository.findFirstByIdVeiculoAndSituacaoAndLidoEmLessThanEqualOrderByLidoEmDescKmDesc(
                eq(idVeiculo), eq(SituacaoLeituraOdometro.VALIDA), any())).thenReturn(Optional.of(anterior));
    }

    @Test
    void lancamentoManualIncoerenteEhRecusado() {
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsuarioLogadoId).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> useCases.registrarManual(idVeiculo,
                    new RegistrarLeituraRequest(49_000L, OffsetDateTime.now())))
                    .isInstanceOf(InvarianteVioladaException.class)
                    .hasMessageContaining("Confira o valor digitado");
            verify(repository, never()).save(any());
        }
    }

    @Test
    void leituraDoCampoIncoerenteEhGuardadaComoInconsistente() {
        UUID idChecklist = UUID.randomUUID();

        var resultado = useCases.registrarLeitura(idVeiculo, 49_000, OffsetDateTime.now(),
                FrotaOdometroApi.OrigemLeitura.CHECKLIST, idChecklist, UUID.randomUUID());

        assertThat(resultado.valida()).isFalse();
        assertThat(resultado.motivoInconsistencia()).contains("menor que a leitura anterior");
        verify(repository).save(any(LeituraOdometro.class));
    }

    @Test
    void mesmoRegistroDeOrigemNaoDuplicaALeitura() {
        UUID idChecklist = UUID.randomUUID();
        var existente = LeituraOdometro.registrar(idVeiculo, 50_100, OffsetDateTime.now(),
                OrigemLeituraOdometro.CHECKLIST, idChecklist, null, anterior, null, 1500);
        when(repository.findByOrigemAndIdOrigem(OrigemLeituraOdometro.CHECKLIST, idChecklist))
                .thenReturn(Optional.of(existente));

        useCases.registrarLeitura(idVeiculo, 50_100, OffsetDateTime.now(),
                FrotaOdometroApi.OrigemLeitura.CHECKLIST, idChecklist, null);

        verify(repository, never()).save(any());
    }
}
