package com.nexo.app.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FeriadosNacionaisTest {

    @Test
    @DisplayName("Reconhece os feriados nacionais fixos")
    void fixos() {
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 10, 12))).isTrue();
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 11, 2))).isTrue();
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 11, 20))).isTrue();
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 10, 13))).isFalse();
    }

    @Test
    @DisplayName("A Sexta-feira Santa conta; Carnaval e Corpus Christi, não")
    void moveis() {
        assertThat(FeriadosNacionais.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(FeriadosNacionais.pascoa(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 4, 3))).isTrue();
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 2, 17))).isFalse();
        assertThat(FeriadosNacionais.feriado(LocalDate.of(2026, 6, 4))).isFalse();
    }
}
