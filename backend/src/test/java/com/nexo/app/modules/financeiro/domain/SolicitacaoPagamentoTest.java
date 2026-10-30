package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Origem do pagamento — o que o pagamento tem de próprio em relação à compra.
 *
 * <p>O vínculo com a compra é uma referência guardada no núcleo, não uma
 * associação: rastreia a origem e não amarra os valores dos dois documentos.</p>
 */
class SolicitacaoPagamentoTest {

    private static final UUID SOLICITANTE = UUID.randomUUID();
    private static final UUID APROVADOR = UUID.randomUUID();

    private FormularioSolicitacao formularioDePagamento() {
        return new FormularioSolicitacao(TipoSolicitacao.PAGAMENTO, "PADRAO", "Solicitação de Pagamento", null, true);
    }

    private SolicitacaoPagamento novoPagamento(String valor) {
        return novoPagamento(valor, Map.of());
    }

    private SolicitacaoPagamento novoPagamento(String valor, Map<CampoCanonico, Object> extras) {
        Map<CampoCanonico, Object> valores = new HashMap<>(extras);
        valores.put(CampoCanonico.VALOR, new BigDecimal(valor));
        return new SolicitacaoPagamento(formularioDePagamento(),
                NucleoSolicitacao.de(new ValoresCanonicos(valores)), SOLICITANTE);
    }

    @Test
    @DisplayName("Sem compra prévia, o pagamento existe por conta própria")
    void pagamentoAvulsoNaoApontaParaCompra() {
        var pagamento = novoPagamento("450.00", Map.of(
                CampoCanonico.POSSUI_COMPRA_PREVIA, Boolean.FALSE,
                CampoCanonico.MOTIVO_SEM_COMPRA, MotivoSemCompra.DESPESA_MENSAL_RECORRENTE));

        assertNull(pagamento.getSolicitacaoCompraId());
        assertEquals(Boolean.FALSE, pagamento.getNucleo().getPossuiCompraPrevia());
        assertEquals(MotivoSemCompra.DESPESA_MENSAL_RECORRENTE, pagamento.getNucleo().getMotivoSemCompra());
    }

    @Test
    @DisplayName("Com compra prévia, guarda a referência da compra de origem")
    void pagamentoVinculadoGuardaAReferencia() {
        UUID compraId = UUID.randomUUID();
        var pagamento = novoPagamento("2000.00", Map.of(
                CampoCanonico.POSSUI_COMPRA_PREVIA, Boolean.TRUE,
                CampoCanonico.SOLICITACAO_COMPRA, compraId));

        assertEquals(compraId, pagamento.getSolicitacaoCompraId());
        assertEquals(compraId, pagamento.getNucleo().getSolicitacaoCompraId());
    }

    @Test
    @DisplayName("O valor do pagamento não é amarrado ao da compra de origem")
    void valorNaoEhAmarradoAoDaCompra() {
        var compra = compraAprovada("5000.00");
        var pagamento = novoPagamento("6200.00", Map.of(
                CampoCanonico.POSSUI_COMPRA_PREVIA, Boolean.TRUE,
                CampoCanonico.SOLICITACAO_COMPRA, UUID.randomUUID()));

        // Quem autoriza vê de onde o pagamento veio e decide; o domínio não recusa.
        pagamento.submeter();
        pagamento.aprovar(APROVADOR, null, "Valor final negociado acima do orçado");

        assertEquals(new BigDecimal("6200.00"), pagamento.getValorAprovado());
        assertEquals(new BigDecimal("5000.00"), compra.valorVigente());
    }

    @Test
    @DisplayName("Formulário de outro domínio não abre um pagamento")
    void recusaFormularioDeOutroDominio() {
        var formularioDeCompra = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);

        var erro = assertThrows(InvarianteVioladaException.class, () -> new SolicitacaoPagamento(
                formularioDeCompra,
                NucleoSolicitacao.de(new ValoresCanonicos(Map.of(CampoCanonico.VALOR, BigDecimal.TEN))),
                SOLICITANTE));
        assertTrue(erro.getMessage().contains("não é um formulário de pagamento"));
    }

    @Test
    @DisplayName("Segue a mesma máquina de estados da compra")
    void seguirOMesmoCicloDeVida() {
        var pagamento = novoPagamento("900.00");
        assertEquals(StatusSolicitacao.RASCUNHO, pagamento.getStatus());

        pagamento.submeter();
        pagamento.aprovar(APROVADOR, new BigDecimal("850.00"), null);

        assertEquals(StatusSolicitacao.APROVADO, pagamento.getStatus());
        assertEquals(new BigDecimal("850.00"), pagamento.valorVigente());
    }

    private SolicitacaoCompra compraAprovada(String valor) {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        var compra = new SolicitacaoCompra(formulario,
                NucleoSolicitacao.de(new ValoresCanonicos(Map.of(CampoCanonico.VALOR, new BigDecimal(valor)))),
                SOLICITANTE);
        compra.submeter();
        compra.aprovar(APROVADOR, null, null);
        return compra;
    }
}
