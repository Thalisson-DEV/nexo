package com.nexo.app.modules.obras.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcorrenciaObraTest {

    @Test
    void normalizaNfkcCaixaAltaEespacosUnicode() {
        assertThat(OcorrenciaObra.normalizarCodigo("  \uFF2F\uFF33\u00a0 12345  "))
                .isEqualTo("OS 12345");
    }

    @Test
    void rejeitaVazioControleENormalizacaoAcimaDoLimite() {
        assertThatThrownBy(() -> OcorrenciaObra.normalizarCodigo("  "))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> OcorrenciaObra.normalizarCodigo("OC-10\n42"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("controle");
        assertThatThrownBy(() -> OcorrenciaObra.normalizarCodigo("a".repeat(101)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("100");
    }

    @Test
    void preservaPrefixoEReutilizaChaveCanonica() {
        assertThat(OcorrenciaObra.normalizarCodigo("oc-1042")).isEqualTo("OC-1042");
        assertThat(OcorrenciaObra.normalizarCodigo("  os   12345 "))
                .isEqualTo(OcorrenciaObra.normalizarCodigo("\uFF2F\uFF33 12345"));
    }
}
