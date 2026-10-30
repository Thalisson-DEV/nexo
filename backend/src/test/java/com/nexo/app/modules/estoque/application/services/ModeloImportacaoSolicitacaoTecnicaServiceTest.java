package com.nexo.app.modules.estoque.application.services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ModeloImportacaoSolicitacaoTecnicaServiceTest {

    private final ModeloImportacaoSolicitacaoTecnicaService service =
            new ModeloImportacaoSolicitacaoTecnicaService();

    @Test
    void csvContemInstrucoesCabecalhosExemploETipoMovimentacao() {
        String csv = new String(service.gerarCsv(), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFF# Modelo de Importacao de Solicitacoes Tecnicas de Material");
        assertThat(csv).contains(String.join(";", ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS));
        assertThat(csv).contains("OBRA-1001;;ALMOX CENTRAL");
        assertThat(csv).contains(";221;");
    }

    @Test
    void csvUsaBomSeparadorPontoEVirgulaInstrucoesECabecalhos() {
        String csv = new String(service.gerarCsv(), StandardCharsets.UTF_8);

        assertThat(csv.getBytes(StandardCharsets.UTF_8)).startsWith(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }
}
