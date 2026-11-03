package com.nexo.app.modules.obras.application.usecases;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FechamentoObraUseCasesTest {

    @Test
    void codigosColadosDeUmaColunaDePlanilha() {
        assertThat(FechamentoObraUseCases.codigos("1053015\r\n1283327\n\n 1053015 \t7000001;7000002, 7000003"))
                .containsExactly("1053015", "1283327", "7000001", "7000002", "7000003");
    }

    @Test
    void textoVazioNaoTemCodigos() {
        assertThat(FechamentoObraUseCases.codigos("  \n ")).isEmpty();
        assertThat(FechamentoObraUseCases.codigos(null)).isEmpty();
    }
}
