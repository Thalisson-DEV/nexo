package com.nexo.app.shared.application.lookup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LookupNormalizerTest {

    @Test
    void normalizaEspacosAcentosEMinusculasComLocaleEstavel() {
        assertThat(LookupNormalizer.normalize("  JOÃO D'ÁVILA  "))
                .isEqualTo("joao d'avila");
    }

    @Test
    void mantemNullEConverteStringEmBrancoParaVazia() {
        assertThat(LookupNormalizer.normalize(null)).isNull();
        assertThat(LookupNormalizer.normalizar(" \t ")).isEmpty();
    }

    @Test
    void validaTamanhoMinimoAposNormalizacao() {
        assertThat(LookupNormalizer.isLongEnough(" á ")).isFalse();
        assertThat(LookupNormalizer.isLongEnough(" Áb ")).isTrue();
        assertThat(LookupConstants.queryLongEnough(null)).isFalse();
    }
}
