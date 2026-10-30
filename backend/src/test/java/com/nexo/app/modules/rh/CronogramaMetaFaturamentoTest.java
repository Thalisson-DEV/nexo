package com.nexo.app.modules.rh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento.CICLO_COMERCIAL;
import static com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento.MENSAL;
import static org.assertj.core.api.Assertions.assertThat;

class CronogramaMetaFaturamentoTest {

    @Test
    @DisplayName("O ciclo comercial vai do dia 26 ao 25 e leva o nome do mês em que termina")
    void cicloComercial() {
        JanelaMetaFaturamento antesDoCorte = CICLO_COMERCIAL.janelaEm(LocalDate.of(2026, 10, 25));
        JanelaMetaFaturamento noCorte = CICLO_COMERCIAL.janelaEm(LocalDate.of(2026, 10, 26));

        assertThat(antesDoCorte.inicio()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(antesDoCorte.fim()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(antesDoCorte.competencia()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(noCorte.inicio()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(noCorte.fim()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(noCorte.competencia()).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    @DisplayName("O ciclo de janeiro começa em 26 de dezembro do ano anterior")
    void cicloNaViradaDoAno() {
        JanelaMetaFaturamento janela = CICLO_COMERCIAL.janelaEm(LocalDate.of(2026, 12, 30));

        assertThat(janela.inicio()).isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(janela.fim()).isEqualTo(LocalDate.of(2027, 1, 25));
        assertThat(janela.competencia()).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    @DisplayName("A meta mensal usa o mês civil")
    void mensal() {
        JanelaMetaFaturamento janela = MENSAL.janelaEm(LocalDate.of(2026, 2, 14));

        assertThat(janela.inicio()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(janela.fim()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(MENSAL.iniciaJanela(LocalDate.of(2026, 2, 1))).isTrue();
        assertThat(CICLO_COMERCIAL.iniciaJanela(LocalDate.of(2026, 2, 1))).isFalse();
    }

    @Test
    @DisplayName("A meta diária do ciclo divide o valor pelos dias úteis do ciclo")
    void metaDiariaDoCiclo() {
        // Ciclo de outubro/2026 (26/09 a 25/10) tem 19 dias úteis (12/10 é feriado).
        var cronograma = CronogramaMetaFaturamento.constante(new BigDecimal("19000"), CICLO_COMERCIAL);

        assertThat(CICLO_COMERCIAL.janelaEm(LocalDate.of(2026, 10, 1)).diasUteis()).isEqualTo(19);
        assertThat(cronograma.metaDiaria(LocalDate.of(2026, 10, 1))).isEqualByComparingTo("1000.00");
        assertThat(cronograma.metaDiaria(LocalDate.of(2026, 10, 3))).isNull();
        assertThat(cronograma.metaProporcional(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25)))
                .isEqualByComparingTo("19000.00");
    }

    @Test
    @DisplayName("Um mês civil soma a parte de cada ciclo que ele atravessa")
    void mesCivilAtravessaDoisCiclos() {
        var cronograma = CronogramaMetaFaturamento.constante(new BigDecimal("19000"), CICLO_COMERCIAL);

        // 01 a 25/10: 16 dias úteis do ciclo de outubro (1.000/dia) = 16.000.
        // 26 a 31/10: 5 dias úteis do ciclo de novembro (21 dias úteis, sem 02 e 20/11) = 19.000 × 5/21.
        assertThat(cronograma.metaProporcional(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .isEqualByComparingTo("20523.81");
    }

    @Test
    @DisplayName("Cada dia usa a meta vigente nele; fora das vigências não há meta")
    void vigenciasEncadeadas() {
        var cronograma = new CronogramaMetaFaturamento(List.of(
                new CronogramaMetaFaturamento.Vigencia(new BigDecimal("20000"), CICLO_COMERCIAL,
                        LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25)),
                new CronogramaMetaFaturamento.Vigencia(new BigDecimal("21000"), CICLO_COMERCIAL,
                        LocalDate.of(2026, 10, 26), null)));

        assertThat(cronograma.valorDaJanelaEm(LocalDate.of(2026, 10, 20))).isEqualByComparingTo("20000");
        assertThat(cronograma.valorDaJanelaEm(LocalDate.of(2026, 10, 27))).isEqualByComparingTo("21000");
        assertThat(cronograma.metaDiaria(LocalDate.of(2026, 10, 27))).isEqualByComparingTo("1000.00");
        assertThat(cronograma.valorDaJanelaEm(LocalDate.of(2026, 9, 1))).isNull();
        assertThat(cronograma.metaProporcional(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isNull();
        assertThat(CronogramaMetaFaturamento.vazio().metaProporcional(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).isNull();
    }
}
