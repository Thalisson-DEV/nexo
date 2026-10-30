package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Máquina de estados e invariantes de decisão da solicitação de compra. */
class SolicitacaoCompraTest {

    private static final UUID SOLICITANTE = UUID.randomUUID();
    private static final UUID APROVADOR = UUID.randomUUID();

    private FormularioSolicitacao formulario() {
        return new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Solicitação de Compra", null, true);
    }

    private NucleoSolicitacao nucleoCom(BigDecimal valor) {
        return NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, valor,
                CampoCanonico.DESCRICAO, "Compra de material elétrico")));
    }

    private SolicitacaoCompra novaCompra(BigDecimal valor) {
        return new SolicitacaoCompra(formulario(), nucleoCom(valor), SOLICITANTE);
    }

    @Test
    @DisplayName("Nasce em rascunho e só entra na fila após a submissão")
    void nasceEmRascunho() {
        var compra = novaCompra(new BigDecimal("1500.00"));
        assertEquals(StatusSolicitacao.RASCUNHO, compra.getStatus());

        compra.submeter();
        assertEquals(StatusSolicitacao.SOLICITADO, compra.getStatus());
        assertNotNull(compra.getDataSubmissao());
    }

    @Test
    @DisplayName("Não submete sem valor — é ele que a diretoria autoriza")
    void naoSubmeteSemValor() {
        var compra = new SolicitacaoCompra(formulario(), NucleoSolicitacao.de(ValoresCanonicos.vazio()),
                SOLICITANTE);

        var erro = assertThrows(InvarianteVioladaException.class, compra::submeter);
        assertTrue(erro.getMessage().contains("valor"));
    }

    @Test
    @DisplayName("Ressubmeter o que já está na fila não altera a data original")
    void ressubmeterEhIdempotente() {
        var compra = novaCompra(new BigDecimal("100.00"));
        compra.submeter();
        var dataOriginal = compra.getDataSubmissao();

        compra.submeter();
        assertEquals(dataOriginal, compra.getDataSubmissao());
    }

    @Test
    @DisplayName("Aprova registrando decisor, valor autorizado e observação")
    void aprovaRegistrandoRastreabilidade() {
        var compra = novaCompra(new BigDecimal("1500.00"));
        compra.submeter();

        compra.aprovar(APROVADOR, new BigDecimal("1450.00"), "Menor orçamento");

        assertEquals(StatusSolicitacao.APROVADO, compra.getStatus());
        assertEquals(new BigDecimal("1450.00"), compra.getValorAprovado());
        assertEquals(APROVADOR, compra.getUsuarioDecisaoId());
        assertEquals("Menor orçamento", compra.getObservacaoDecisao());
        assertNotNull(compra.getDataDecisao());
        assertEquals(new BigDecimal("1450.00"), compra.valorVigente());
    }

    @Test
    @DisplayName("Sem valor informado, aprova pelo valor solicitado")
    void aprovaPeloValorSolicitadoQuandoOmitido() {
        var compra = novaCompra(new BigDecimal("800.00"));
        compra.submeter();

        compra.aprovar(APROVADOR, null, null);

        assertEquals(new BigDecimal("800.00"), compra.getValorAprovado());
    }

    @Test
    @DisplayName("Não aprova por valor maior que o solicitado — ninguém apresentou o excedente")
    void naoAprovaAcimaDoSolicitado() {
        var compra = novaCompra(new BigDecimal("1000.00"));
        compra.submeter();

        var erro = assertThrows(InvarianteVioladaException.class,
                () -> compra.aprovar(APROVADOR, new BigDecimal("1200.00"), null));
        assertTrue(erro.getMessage().contains("não pode superar"));
    }

    @Test
    @DisplayName("Só decide o que está aguardando decisão")
    void soDecideOQueEstaNaFila() {
        var rascunho = novaCompra(new BigDecimal("500.00"));
        assertThrows(InvarianteVioladaException.class, () -> rascunho.aprovar(APROVADOR, null, null));

        var aprovada = novaCompra(new BigDecimal("500.00"));
        aprovada.submeter();
        aprovada.aprovar(APROVADOR, null, null);
        assertThrows(InvarianteVioladaException.class,
                () -> aprovada.rejeitar(APROVADOR, "Fora do orçamento"));
    }

    @Test
    @DisplayName("Rejeição e cancelamento exigem justificativa")
    void desfechosNegativosExigemJustificativa() {
        var compra = novaCompra(new BigDecimal("500.00"));
        compra.submeter();
        assertThrows(InvarianteVioladaException.class, () -> compra.rejeitar(APROVADOR, "  "));

        compra.rejeitar(APROVADOR, "Fora do orçamento do mês");
        assertEquals(StatusSolicitacao.REJEITADO, compra.getStatus());
        assertEquals("Fora do orçamento do mês", compra.getMotivoRejeicao());

        var outra = novaCompra(new BigDecimal("500.00"));
        assertThrows(InvarianteVioladaException.class, () -> outra.cancelar(SOLICITANTE, null));
    }

    @Test
    @DisplayName("Solicitação finalizada não é cancelada nem editada")
    void finalizadaNaoMudaMais() {
        var compra = novaCompra(new BigDecimal("500.00"));
        compra.submeter();
        compra.aprovar(APROVADOR, null, null);

        assertThrows(InvarianteVioladaException.class, () -> compra.cancelar(SOLICITANTE, "Desisti"));
        assertThrows(InvarianteVioladaException.class, compra::exigirEditavel);
        assertThrows(InvarianteVioladaException.class,
                () -> compra.atualizarConteudo(nucleoCom(new BigDecimal("300.00"))));
    }

    @Test
    @DisplayName("Conteúdo é substituído por completo enquanto não houver decisão")
    void atualizaConteudoIntegralmente() {
        var compra = novaCompra(new BigDecimal("500.00"));

        compra.atualizarConteudo(NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("750.00"),
                CampoCanonico.NATUREZA_SERVICO, NaturezaServicoFrota.MECANICA))));

        assertEquals(new BigDecimal("750.00"), compra.getNucleo().getValor());
        assertEquals(NaturezaServicoFrota.MECANICA, compra.getNucleo().getNaturezaServico());
        // Substituição integral: o que não veio no novo núcleo não sobrevive.
        assertEquals(null, compra.getNucleo().getDescricao());
    }

    @Test
    @DisplayName("Formulário de outro domínio não abre uma compra")
    void recusaFormularioDeOutroDominio() {
        var formularioDePagamento = new FormularioSolicitacao(
                TipoSolicitacao.PAGAMENTO, "PADRAO", "Pagamento", null, true);

        assertThrows(InvarianteVioladaException.class, () -> new SolicitacaoCompra(
                formularioDePagamento, nucleoCom(BigDecimal.TEN), SOLICITANTE));
    }

    @Test
    @DisplayName("Cancelar registra quem desistiu e por quê")
    void cancelamentoRegistraAutoria() {
        var compra = novaCompra(new BigDecimal("500.00"));

        compra.cancelar(SOLICITANTE, "Material veio do estoque");

        assertEquals(StatusSolicitacao.CANCELADO, compra.getStatus());
        assertEquals(SOLICITANTE, compra.getUsuarioCancelamentoId());
        assertEquals("Material veio do estoque", compra.getMotivoCancelamento());
        assertNotNull(compra.getDataCancelamento());
    }

    @Test
    @DisplayName("Desfazer a aprovação devolve a compra à fila e limpa a decisão")
    void desfazerAprovacao() {
        var compra = novaCompra(new BigDecimal("500.00"));
        compra.submeter();
        compra.aprovar(APROVADOR, new BigDecimal("450.00"), "Negociado");

        var anterior = compra.desfazerDecisao("Valor errado");

        assertEquals(StatusSolicitacao.SOLICITADO, compra.getStatus());
        assertNull(compra.getValorAprovado());
        assertNull(compra.getUsuarioDecisaoId());
        assertNull(compra.getDataDecisao());
        assertNull(compra.getObservacaoDecisao());
        assertNotNull(compra.getDataSubmissao());
        assertEquals(StatusSolicitacao.APROVADO, anterior.statusAnterior());
        assertEquals(APROVADOR, anterior.usuarioDecisaoAnterior());
        assertEquals(new BigDecimal("450.00"), anterior.valorAprovadoAnterior());
    }

    @Test
    @DisplayName("Desfazer a rejeição limpa o motivo e permite nova decisão")
    void desfazerRejeicao() {
        var compra = novaCompra(new BigDecimal("500.00"));
        compra.submeter();
        compra.rejeitar(APROVADOR, "Sem orçamento");

        var anterior = compra.desfazerDecisao("Orçamento liberado");

        assertEquals(StatusSolicitacao.REJEITADO, anterior.statusAnterior());
        assertNull(compra.getMotivoRejeicao());
        compra.aprovar(APROVADOR, null, null);
        assertEquals(StatusSolicitacao.APROVADO, compra.getStatus());
    }

    @Test
    @DisplayName("Só desfaz decisão de aprovada ou rejeitada, e sempre com motivo")
    void desfazerExigeDecisaoEMotivo() {
        var compra = novaCompra(new BigDecimal("500.00"));
        compra.submeter();
        assertThrows(InvarianteVioladaException.class, () -> compra.desfazerDecisao("Motivo"));

        compra.aprovar(APROVADOR, null, null);
        var erro = assertThrows(InvarianteVioladaException.class, () -> compra.desfazerDecisao("  "));
        assertTrue(erro.getMessage().contains("por que"));
        assertEquals(StatusSolicitacao.APROVADO, compra.getStatus());
    }
}
