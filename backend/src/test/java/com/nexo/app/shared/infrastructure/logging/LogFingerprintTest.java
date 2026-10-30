package com.nexo.app.shared.infrastructure.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogFingerprintTest {

    @Test
    void correlacionaSemExporValorOuPermitirForjamentoDeLinha() {
        String controladoPeloCliente = "segredo-integral\r\nWARN evento-forjado";

        String fingerprint = LogFingerprint.de(controladoPeloCliente);

        assertThat(fingerprint)
                .matches("sha256=[0-9a-f]{16};length=\\d+")
                .doesNotContain("segredo", "evento-forjado", "\r", "\n")
                .isEqualTo(LogFingerprint.de(controladoPeloCliente));
        assertThat(LogFingerprint.de(controladoPeloCliente + "-outro"))
                .isNotEqualTo(fingerprint);
    }
}
