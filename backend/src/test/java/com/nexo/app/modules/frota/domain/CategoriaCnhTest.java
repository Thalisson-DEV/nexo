package com.nexo.app.modules.frota.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriaCnhTest {

    @Test
    void categoriaSuperiorHabilitaAsInferioresDeQuatroRodas() {
        assertThat(CategoriaCnh.D.habilita(CategoriaCnh.B)).isTrue();
        assertThat(CategoriaCnh.D.habilita(CategoriaCnh.C)).isTrue();
        assertThat(CategoriaCnh.E.habilita(CategoriaCnh.D)).isTrue();
    }

    @Test
    void categoriaInferiorNaoHabilitaSuperior() {
        assertThat(CategoriaCnh.B.habilita(CategoriaCnh.C)).isFalse();
        assertThat(CategoriaCnh.C.habilita(CategoriaCnh.D)).isFalse();
    }

    @Test
    void motoSoComCategoriaA() {
        assertThat(CategoriaCnh.AB.habilita(CategoriaCnh.A)).isTrue();
        assertThat(CategoriaCnh.D.habilita(CategoriaCnh.A)).isFalse();
        assertThat(CategoriaCnh.A.habilita(CategoriaCnh.B)).isFalse();
    }

    @Test
    void combinadaUsaASegundaLetraParaQuatroRodas() {
        assertThat(CategoriaCnh.AD.habilita(CategoriaCnh.C)).isTrue();
        assertThat(CategoriaCnh.AB.habilita(CategoriaCnh.C)).isFalse();
    }

    @Test
    void semExigenciaQualquerUmaHabilita() {
        assertThat(CategoriaCnh.B.habilita(null)).isTrue();
    }
}
