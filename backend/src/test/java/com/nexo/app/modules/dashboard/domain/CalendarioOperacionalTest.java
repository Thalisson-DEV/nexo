package com.nexo.app.modules.dashboard.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioOperacionalTest {

    // Outubro/2026: começa numa quinta-feira e tem 21 dias úteis (12/10 é feriado).
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    @Test
    @DisplayName("Conta dias úteis de segunda a sexta, sem feriados nacionais")
    void contaDiasUteis() {
        assertThat(CalendarioOperacional.diasUteis(OUTUBRO)).isEqualTo(21);
        // 09/10 (sexta) a 13/10 (terça): sexta e terça; a segunda 12/10 é feriado
        assertThat(CalendarioOperacional.diasUteis(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13))).isEqualTo(2);
        assertThat(CalendarioOperacional.diasUteis(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 9))).isZero();
    }

    @Test
    @DisplayName("O dia de operação é o de São Paulo, não o UTC")
    void diaDeOperacao() {
        OffsetDateTime noiteEmSaoPaulo = OffsetDateTime.parse("2026-10-10T01:30:00Z");

        assertThat(CalendarioOperacional.diaDe(noiteEmSaoPaulo)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(CalendarioOperacional.inicioDoDia(LocalDate.of(2026, 10, 9)))
                .isEqualTo(OffsetDateTime.parse("2026-10-09T00:00:00-03:00"));
    }

    @Test
    @DisplayName("Percentual com uma casa e nulo quando não há base")
    void percentual() {
        assertThat(CalendarioOperacional.percentual(new BigDecimal("750"), new BigDecimal("1000")))
                .isEqualByComparingTo("75.0");
        assertThat(CalendarioOperacional.percentual(BigDecimal.TEN, BigDecimal.ZERO)).isNull();
    }
}
