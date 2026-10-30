package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CondutorTest {

    @Test
    void registraCnhSoComDigitosEMascaraNaExibicao() {
        var condutor = new Condutor(UUID.randomUUID());

        condutor.registrarCnh("123.456.789-01", CategoriaCnh.D, LocalDate.now().plusYears(2), null, null);

        assertThat(condutor.getCnhNumero()).isEqualTo("12345678901");
        assertThat(condutor.cnhNumeroMascarado()).isEqualTo("*******8901");
        assertThat(condutor.getCnhNumeroHash()).isNotBlank();
    }

    @Test
    void recusaNumeroComTamanhoErrado() {
        var condutor = new Condutor(UUID.randomUUID());

        assertThatThrownBy(() -> condutor.registrarCnh("1234", CategoriaCnh.B, LocalDate.now(), null, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void cnhVencidaImpedeConduzir() {
        var condutor = new Condutor(UUID.randomUUID());
        condutor.registrarCnh("12345678901", CategoriaCnh.D, LocalDate.of(2026, 1, 1), null, null);

        assertThatThrownBy(() -> condutor.exigirHabilitadoPara(null, LocalDate.of(2026, 1, 2)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("vencida");
    }

    @Test
    void categoriaInsuficienteImpedeConduzir() {
        var condutor = new Condutor(UUID.randomUUID());
        condutor.registrarCnh("12345678901", CategoriaCnh.B, LocalDate.now().plusYears(1), null, null);

        assertThatThrownBy(() -> condutor.exigirHabilitadoPara(CategoriaCnh.C, LocalDate.now()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("não habilita");
    }
}
