package com.nexo.app.modules.obras.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.obras.application.dto.ProjetoRelacionadoResponse;
import com.nexo.app.modules.obras.application.dto.VinculoProjetoRequest;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.RelacionamentoProjeto;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.RelacionamentoProjetoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelacionamentoProjetoUseCasesTest {

    @Mock
    private RelacionamentoProjetoRepository repository;
    @Mock
    private ObraRepository obraRepository;

    private RelacionamentoProjetoUseCases useCases;

    @BeforeEach
    void setUp() {
        useCases = new RelacionamentoProjetoUseCases(repository, obraRepository);
    }

    @Test
    void deveCriarParCanonicoMesmoQuandoRecebidoNaOrdemInversa() {
        UUID menor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID maior = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        Obra projetoMenor = obra(menor, "1000001");
        Obra projetoMaior = obra(maior, "1000002");
        when(obraRepository.findById(maior)).thenReturn(Optional.of(projetoMaior));
        when(obraRepository.findById(menor)).thenReturn(Optional.of(projetoMenor));
        when(repository.findByProjetoAIdAndProjetoBId(menor, maior)).thenReturn(Optional.empty());
        when(repository.save(any(RelacionamentoProjeto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjetoRelacionadoResponse response = useCases.vincular(
                maior, new VinculoProjetoRequest(menor, TipoVinculoProjeto.KIT));

        ArgumentCaptor<RelacionamentoProjeto> captor = ArgumentCaptor.forClass(RelacionamentoProjeto.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getProjetoA().getId()).isEqualTo(menor);
        assertThat(captor.getValue().getProjetoB().getId()).isEqualTo(maior);
        assertThat(response.idProjeto()).isEqualTo(menor);
        assertThat(response.tipoVinculo()).isEqualTo(TipoVinculoProjeto.KIT);
    }

    @Test
    void deveListarOMesmoVinculoPelosDoisProjetosSemHierarquiaNaSerializacao() throws Exception {
        UUID primeiroId = UUID.randomUUID();
        UUID segundoId = UUID.randomUUID();
        RelacionamentoProjeto relacionamento = new RelacionamentoProjeto(
                obra(primeiroId, "1000001"), obra(segundoId, "1000002"), TipoVinculoProjeto.MEDIDOR);
        when(obraRepository.existsById(primeiroId)).thenReturn(true);
        when(obraRepository.existsById(segundoId)).thenReturn(true);
        when(repository.findAtivosPorProjeto(primeiroId)).thenReturn(List.of(relacionamento));
        when(repository.findAtivosPorProjeto(segundoId)).thenReturn(List.of(relacionamento));

        ProjetoRelacionadoResponse vistoDoPrimeiro = useCases.listar(primeiroId).getFirst();
        ProjetoRelacionadoResponse vistoDoSegundo = useCases.listar(segundoId).getFirst();

        assertThat(vistoDoPrimeiro.idProjeto()).isEqualTo(segundoId);
        assertThat(vistoDoSegundo.idProjeto()).isEqualTo(primeiroId);
        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(vistoDoPrimeiro).toLowerCase();
        assertThat(json).doesNotContain("pai", "filho", "subprojeto");
    }

    @Test
    void deveRejeitarDuplicidadeEAutoRelacionamento() {
        UUID primeiroId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID segundoId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Obra primeiro = obra(primeiroId, "1000001");
        Obra segundo = obra(segundoId, "1000002");
        RelacionamentoProjeto existente =
                new RelacionamentoProjeto(primeiro, segundo, TipoVinculoProjeto.KIT);
        when(obraRepository.findById(primeiroId)).thenReturn(Optional.of(primeiro));
        when(obraRepository.findById(segundoId)).thenReturn(Optional.of(segundo));
        when(repository.findByProjetoAIdAndProjetoBId(primeiroId, segundoId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCases.vincular(
                primeiroId, new VinculoProjetoRequest(segundoId, TipoVinculoProjeto.KIT)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("já estão relacionados");
        assertThatThrownBy(() -> useCases.vincular(
                primeiroId, new VinculoProjetoRequest(primeiroId, TipoVinculoProjeto.KIT)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("si mesmo");
    }

    private Obra obra(UUID id, String numero) {
        Obra obra = new Obra(
                numero, "PROJETO " + numero, null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new StatusObra("PLANEJADA", 1, false), null, null
        );
        ReflectionTestUtils.setField(obra, "id", id);
        return obra;
    }
}
