package com.nexo.app.modules.estoque.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TipoSolicitacaoTecnicaTest {

    @ParameterizedTest
    @CsvSource({
            "221, ADICAO_COMUM, true, 221",
            "921, ADICAO_CONCRETO, true, 921",
            "222, DEVOLUCAO_COMUM, false, 222",
            "922, DEVOLUCAO_CONCRETO, false, 922",
            "' 221 ', ADICAO_COMUM, true, 221",
            "'221 - Adição Comum', ADICAO_COMUM, true, 221",
            "'ADICAO_COMUM', ADICAO_COMUM, true, 221",
            "'Adição Comum', ADICAO_COMUM, true, 221",
            "'921 - Adição Concreto', ADICAO_CONCRETO, true, 921",
            "'222 - Devolução Comum', DEVOLUCAO_COMUM, false, 222",
            "'Devolucao Comum', DEVOLUCAO_COMUM, false, 222",
            "'922 - Devolução Concreto', DEVOLUCAO_CONCRETO, false, 922"
    })
    void resolveCodigoInformadoESemanticaOperacional(
            String codigo,
            TipoSolicitacaoTecnica esperado,
            boolean adicao,
            String codigoEsperado
    ) {
        TipoSolicitacaoTecnica tipo = TipoSolicitacaoTecnica.porCodigo(codigo);

        assertThat(tipo).isEqualTo(esperado);
        assertThat(tipo.getCodigo()).isEqualTo(codigoEsperado);
        assertThat(tipo.isAdicao()).isEqualTo(adicao);
        assertThat(tipo.isDevolucao()).isNotEqualTo(adicao);
    }
}
