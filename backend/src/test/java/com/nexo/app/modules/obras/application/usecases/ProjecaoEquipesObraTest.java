package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.TurnoProgramacao;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository.ValorProgramado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjecaoEquipesObraTest {

    @Test
    void capacidadeDescontaPlataformaEDeslocamentoIdaEVolta() {
        assertThat(ProjecaoEquipesObra.capacidadeMinutos(480, 30, 60)).isEqualTo(330);
    }

    @Test
    void deslocamentoMaiorQueAJornadaDeixaACapacidadeNegativa() {
        // 8h de jornada, 30 min de plataforma e 9h16 de deslocamento ida e volta.
        assertThat(ProjecaoEquipesObra.capacidadeMinutos(480, 30, 278)).isEqualTo(-106);
    }

    @Test
    void semCoordenadasNaoDescontaDeslocamento() {
        assertThat(ProjecaoEquipesObra.capacidadeMinutos(480, 30, null)).isEqualTo(450);
    }

    @Test
    void semJornadaACapacidadeFicaDesconhecida() {
        assertThat(ProjecaoEquipesObra.capacidadeMinutos(null, 30, 10)).isNull();
    }

    @Test
    void programacaoQuePassaDoPeriodoContaSoOsDiasUteisDentroDele() {
        // Segunda 09/11 a sexta 13/11, sem feriado: 5 dias úteis, 2 no período.
        ValorProgramado programada = new ValorProgramado(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), TurnoProgramacao.DIA_TODO,
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), new BigDecimal("1000.00"));

        BigDecimal parte = ProjecaoEquipesObra.parteNoPeriodo(programada, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 30));

        assertThat(parte).isEqualByComparingTo("400.00");
    }
}
