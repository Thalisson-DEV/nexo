package com.nexo.app.shared.application;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelecaoMultiplaTest {

    @Test
    void normalizarRemoveNulosDuplicadosENuncaDevolveNula() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(SelecaoMultipla.normalizar(null)).isEmpty();
        assertThat(SelecaoMultipla.normalizar(List.of())).isEmpty();
        assertThat(SelecaoMultipla.normalizar(Arrays.asList(a, null, b, a))).containsExactly(a, b);
    }

    @Test
    void selecaoVaziaNaoRestringe() {
        assertThat(SelecaoMultipla.contem(null, UUID.randomUUID())).isTrue();
        assertThat(SelecaoMultipla.contem(List.of(), UUID.randomUUID())).isTrue();
    }

    @Test
    void selecaoExplicitaAceitaQualquerUmDosEscolhidos() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(SelecaoMultipla.contem(List.of(a, b), b)).isTrue();
        assertThat(SelecaoMultipla.contem(List.of(a, b), UUID.randomUUID())).isFalse();
    }

    /** Registro sem a dimensão preenchida (ex.: colaborador sem base) fica fora do recorte. */
    @Test
    void valorNuloNuncaCasaComSelecaoExplicita() {
        assertThat(SelecaoMultipla.contem(List.of(UUID.randomUUID()), null)).isFalse();
    }
}
