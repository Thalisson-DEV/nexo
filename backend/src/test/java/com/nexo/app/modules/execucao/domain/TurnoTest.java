package com.nexo.app.modules.execucao.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

class TurnoTest {

    @Test
    @DisplayName("Turno guarda o retrato da composição confirmada na abertura")
    void registraComposicao() {
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID());
        UUID idVeiculo = UUID.randomUUID();
        UUID lider = UUID.randomUUID();
        UUID parceiro = UUID.randomUUID();

        turno.registrarComposicao(idVeiculo, List.of(lider, parceiro));

        assertThat(turno.getIdVeiculo()).isEqualTo(idVeiculo);
        assertThat(turno.getIdsColaboradores()).containsExactly(lider, parceiro);
    }

    @Test
    @DisplayName("Composição de turno fechado não pode ser alterada")
    void naoAlteraTurnoFechado() {
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID());
        turno.fechar();

        assertThatThrownBy(() -> turno.registrarComposicao(UUID.randomUUID(), List.of()))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    @DisplayName("Fechar turno já fechado deve ser idempotente e manter a data de fim original")
    void fecharTurno_duasVezes_deveSerIdempotente() {
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID());
        turno.fechar();
        var dataFimOriginal = turno.getDataFim();

        // Segunda chamada não deve lançar exceção
        turno.fechar();

        assertThat(turno.getStatus()).isEqualTo(StatusTurno.FECHADO);
        assertThat(turno.getDataFim()).isEqualTo(dataFimOriginal);
    }

    @Test
    @DisplayName("Líder precisa estar na composição; substituto fora dela é ignorado")
    void composicaoPeloSupervisor() {
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID());
        UUID lider = UUID.randomUUID();
        UUID substituto = UUID.randomUUID();

        assertThatThrownBy(() -> turno.informarComposicao(null, List.of(substituto), lider, List.of()))
                .isInstanceOf(InvarianteVioladaException.class);

        turno.informarComposicao(null, List.of(lider, substituto), lider, List.of(substituto, UUID.randomUUID()));
        assertThat(turno.getIdLider()).isEqualTo(lider);
        assertThat(turno.getIdsSubstitutos()).containsExactly(substituto);
    }

    @Test
    @DisplayName("Composição pelo supervisor corrige também o turno fechado; a do app continua recusada")
    void corrigeComposicaoDoTurnoFechado() {
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID());
        UUID colaborador = UUID.randomUUID();
        turno.fechar();

        turno.informarComposicao(null, List.of(colaborador), colaborador, List.of());

        assertThat(turno.getIdsColaboradores()).containsExactly(colaborador);
        assertThatThrownBy(() -> turno.registrarComposicao(null, List.of(colaborador)))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    @DisplayName("Fechar no horário informado: nem antes da abertura nem duas vezes")
    void fechaNoHorarioInformado() {
        OffsetDateTime inicio = OffsetDateTime.now().minusHours(9);
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID(), inicio);

        assertThatThrownBy(() -> turno.fecharEm(inicio.minusMinutes(1))).isInstanceOf(InvarianteVioladaException.class);

        turno.fecharEm(inicio.plusHours(8));
        assertThat(turno.getDataFim()).isEqualTo(inicio.plusHours(8));
        assertThat(turno.isEncerradoAutomaticamente()).isFalse();
        assertThatThrownBy(() -> turno.fecharEm(inicio.plusHours(9))).isInstanceOf(InvarianteVioladaException.class);
    }
}
