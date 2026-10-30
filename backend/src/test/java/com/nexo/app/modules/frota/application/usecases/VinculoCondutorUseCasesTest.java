package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.domain.CategoriaCnh;
import com.nexo.app.modules.frota.domain.Condutor;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.OrigemVinculoCondutor;
import com.nexo.app.modules.frota.domain.VinculoCondutor;
import com.nexo.app.modules.frota.infrastructure.persistence.CondutorRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.VinculoCondutorRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VinculoCondutorUseCasesTest {

    @Mock
    private VinculoCondutorRepository repository;
    @Mock
    private FichaVeiculoRepository fichaRepository;
    @Mock
    private CondutorRepository condutorRepository;
    @Mock
    private ReferenciasFrota referencias;
    @InjectMocks
    private VinculoCondutorUseCases useCases;

    private final UUID idVeiculo = UUID.randomUUID();
    private final UUID idColaborador = UUID.randomUUID();
    private final OffsetDateTime agora = OffsetDateTime.now().minusMinutes(1);

    @BeforeEach
    void setUp() {
        lenient().when(fichaRepository.findByIdVeiculo(idVeiculo))
                .thenReturn(Optional.of(FichaVeiculo.provisionar(idVeiculo, "ABC1D23", null)));
        lenient().when(referencias.colaboradorAtivo(any())).thenReturn(true);
        lenient().when(repository.save(any(VinculoCondutor.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void comCnh(LocalDate validade) {
        var condutor = new Condutor(idColaborador);
        condutor.registrarCnh("12345678901", CategoriaCnh.D, validade, null, null);
        when(condutorRepository.findByIdColaborador(idColaborador)).thenReturn(Optional.of(condutor));
    }

    @Test
    void trocaDeCondutorEncerraOVinculoAnteriorNoInstanteDaTroca() {
        comCnh(LocalDate.now().plusYears(1));
        var anterior = new VinculoCondutor(idVeiculo, UUID.randomUUID(), agora.minusDays(5),
                OrigemVinculoCondutor.MANUAL, null, null);
        when(repository.findByIdVeiculoAndFimIsNullAndAtivoTrue(idVeiculo)).thenReturn(Optional.of(anterior));
        when(repository.findByIdColaboradorAndFimIsNullAndAtivoTrue(idColaborador)).thenReturn(Optional.empty());

        var novo = useCases.vincular(idVeiculo, idColaborador, agora, OrigemVinculoCondutor.MANUAL, null, null);

        assertThat(anterior.getFim()).isEqualTo(agora);
        verify(repository).saveAndFlush(anterior);
        assertThat(novo.getIdColaborador()).isEqualTo(idColaborador);
        assertThat(novo.isAberto()).isTrue();
    }

    @Test
    void repetirOVinculoAbertoDevolveOExistente() {
        comCnh(LocalDate.now().plusYears(1));
        var aberto = new VinculoCondutor(idVeiculo, idColaborador, agora.minusDays(1),
                OrigemVinculoCondutor.MANUAL, null, null);
        when(repository.findByIdVeiculoAndFimIsNullAndAtivoTrue(idVeiculo)).thenReturn(Optional.of(aberto));

        var resultado = useCases.vincular(idVeiculo, idColaborador, agora, OrigemVinculoCondutor.CHECKLIST, UUID.randomUUID(), null);

        assertThat(resultado).isSameAs(aberto);
        verify(repository, never()).save(any());
    }

    @Test
    void cnhVencidaImpedeOVinculo() {
        comCnh(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> useCases.vincular(idVeiculo, idColaborador, agora, OrigemVinculoCondutor.MANUAL, null, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("vencida");
    }

    @Test
    void semFichaDeCondutorNaoVincula() {
        when(condutorRepository.findByIdColaborador(idColaborador)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.vincular(idVeiculo, idColaborador, agora, OrigemVinculoCondutor.MANUAL, null, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("CNH");
    }

    @Test
    void naoPermiteSobreporPeriodoEncerrado() {
        comCnh(LocalDate.now().plusYears(1));
        when(repository.findByIdVeiculoAndFimIsNullAndAtivoTrue(idVeiculo)).thenReturn(Optional.empty());
        when(repository.existeEncerradoDepois(idVeiculo, idColaborador, agora)).thenReturn(true);

        assertThatThrownBy(() -> useCases.vincular(idVeiculo, idColaborador, agora, OrigemVinculoCondutor.MANUAL, null, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("sobrepor");
    }
}
