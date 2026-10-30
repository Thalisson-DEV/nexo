package com.nexo.app.modules.execucao.domain;

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
}
