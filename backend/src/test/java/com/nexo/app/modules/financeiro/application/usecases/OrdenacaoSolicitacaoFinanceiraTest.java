package com.nexo.app.modules.financeiro.application.usecases;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdenacaoSolicitacaoFinanceiraTest {

    @Test
    void traduzFornecedorParaAColunaNormalizadaDoNucleo() {
        var pageable = PageRequest.of(2, 25, Sort.by(Sort.Order.desc("fornecedor")));

        var normalizado = OrdenacaoSolicitacaoFinanceira.normalizar(pageable);

        Sort.Order ordem = normalizado.getSort().iterator().next();
        assertEquals("nucleo.fornecedorOrdenacao", ordem.getProperty());
        assertEquals(Sort.Direction.DESC, ordem.getDirection());
        assertEquals(2, normalizado.getPageNumber());
        assertEquals(25, normalizado.getPageSize());
    }
}
