package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.infrastructure.security.HashUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuscaDadosBancariosTest {

    @Test
    void nucleoIndexaPixSemPersistirOValorEmTextoPlanoNaColunaDeBusca() {
        String pix = "financeiro@exemplo.com.br";
        var valores = new ValoresCanonicos(Map.of(
                CampoCanonico.DADOS_PAGAMENTO, "PIX " + pix,
                CampoCanonico.FORNECEDOR_NOME, "Águia Peças"));

        NucleoSolicitacao nucleo = NucleoSolicitacao.de(valores);

        assertFalse(nucleo.getDadosPagamentoBusca().contains(pix));
        assertTrue(nucleo.getDadosPagamentoBusca().contains(HashUtils.gerarBlindIndex(pix)));
        assertEquals("aguia pecas", nucleo.getFornecedorOrdenacao());
    }

    @Test
    void fornecedorReindexaDadosBancariosAoAtualizar() {
        var fornecedor = new Fornecedor("ACME", null, null, "PIX antigo@acme.com", null);
        assertTrue(fornecedor.getDadosBancariosBusca()
                .contains(HashUtils.gerarBlindIndex("antigo@acme.com")));

        fornecedor.atualizar("ACME", null, null, "PIX novo@acme.com", null);

        assertTrue(fornecedor.getDadosBancariosBusca()
                .contains(HashUtils.gerarBlindIndex("novo@acme.com")));
        assertFalse(fornecedor.getDadosBancariosBusca()
                .contains(HashUtils.gerarBlindIndex("antigo@acme.com")));
    }
}
