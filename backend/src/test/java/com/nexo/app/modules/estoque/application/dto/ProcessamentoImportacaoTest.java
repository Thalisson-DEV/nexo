package com.nexo.app.modules.estoque.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessamentoImportacaoTest {
    @Test
    void representaAsEtapasEEstadosFinaisSemEstadoEmAndamentoFalso() {
        assertThat(ProcessamentoImportacao.validacao(2, 0).estado())
                .isEqualTo(ProcessamentoImportacao.Estado.PRONTO_PARA_CONFIRMACAO);
        assertThat(ProcessamentoImportacao.validacao(1, 1).estado())
                .isEqualTo(ProcessamentoImportacao.Estado.COM_INCONSISTENCIAS);
        assertThat(ProcessamentoImportacao.confirmacao(2, 0).estado())
                .isEqualTo(ProcessamentoImportacao.Estado.CONCLUIDO);
        assertThat(ProcessamentoImportacao.confirmacao(1, 1).estado())
                .isEqualTo(ProcessamentoImportacao.Estado.PARCIAL);
        assertThat(ProcessamentoImportacao.confirmacao(0, 1).estado())
                .isEqualTo(ProcessamentoImportacao.Estado.FALHOU);
    }
}
