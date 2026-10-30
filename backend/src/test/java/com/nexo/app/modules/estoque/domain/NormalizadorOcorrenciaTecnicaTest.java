package com.nexo.app.modules.estoque.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizadorOcorrenciaTecnicaTest {

    @Test
    void aplicaNfkcCaixaAltaEColapsoDeEspacosUnicode() {
        assertThat(NormalizadorOcorrenciaTecnica.normalizar("  ｏｓ\u00a0 12345  "))
                .isEqualTo("OS 12345");
    }

    @Test
    void aceitaOcOsEValorNovoSemConverterPrefixo() {
        assertThat(NormalizadorOcorrenciaTecnica.normalizar("oc-1042")).isEqualTo("OC-1042");
        assertThat(NormalizadorOcorrenciaTecnica.normalizar("os 77")).isEqualTo("OS 77");
        assertThat(NormalizadorOcorrenciaTecnica.normalizar("manutencao emergencial 9"))
                .isEqualTo("MANUTENCAO EMERGENCIAL 9");
    }

    @Test
    void variacoesEquivalentesProduzemAMesmaChave() {
        assertThat(NormalizadorOcorrenciaTecnica.normalizar("  os   12345 "))
                .isEqualTo(NormalizadorOcorrenciaTecnica.normalizar("ＯＳ 12345"));
    }

    @Test
    void rejeitaCaracteresDeControle() {
        assertThatThrownBy(() -> NormalizadorOcorrenciaTecnica.normalizar("OC-10\n42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controle");
    }

    @Test
    void validaLimiteDepoisDaNormalizacao() {
        assertThat(NormalizadorOcorrenciaTecnica.normalizar("a".repeat(100)))
                .hasSize(100);
        assertThatThrownBy(() -> NormalizadorOcorrenciaTecnica.normalizar("a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }
}
