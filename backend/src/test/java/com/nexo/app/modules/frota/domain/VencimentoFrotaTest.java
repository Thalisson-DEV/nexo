package com.nexo.app.modules.frota.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VencimentoFrotaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 9);
    private static final List<Integer> FAIXAS = List.of(30, 15, 7, 0);

    @Test
    void escolheAFaixaMaisUrgenteJaAlcancada() {
        assertThat(AlertaVencimento.faixaAlcancada(FAIXAS, HOJE.plusDays(10), HOJE)).isEqualTo(15);
        assertThat(AlertaVencimento.faixaAlcancada(FAIXAS, HOJE.plusDays(7), HOJE)).isEqualTo(7);
        assertThat(AlertaVencimento.faixaAlcancada(FAIXAS, HOJE, HOJE)).isZero();
        assertThat(AlertaVencimento.faixaAlcancada(FAIXAS, HOJE.minusDays(40), HOJE)).isZero();
    }

    @Test
    void foraDeTodasAsFaixasNaoAvisa() {
        assertThat(AlertaVencimento.faixaAlcancada(FAIXAS, HOJE.plusDays(31), HOJE)).isNull();
    }

    @Test
    void situacaoDoVencimento() {
        assertThat(SituacaoVencimento.calcular(null, HOJE, 30)).isEqualTo(SituacaoVencimento.SEM_VENCIMENTO);
        assertThat(SituacaoVencimento.calcular(HOJE.minusDays(1), HOJE, 30)).isEqualTo(SituacaoVencimento.VENCIDO);
        assertThat(SituacaoVencimento.calcular(HOJE, HOJE, 30)).isEqualTo(SituacaoVencimento.A_VENCER);
        assertThat(SituacaoVencimento.calcular(HOJE.plusDays(30), HOJE, 30)).isEqualTo(SituacaoVencimento.A_VENCER);
        assertThat(SituacaoVencimento.calcular(HOJE.plusDays(31), HOJE, 30)).isEqualTo(SituacaoVencimento.VIGENTE);
    }

    @Test
    void parametrosOrdenamFaixasDoMaiorParaOMenorSemRepetir() {
        assertThat(ParametrosFrota.converterFaixas("7, 30,0,15,7")).containsExactly(30, 15, 7, 0);
    }
}
