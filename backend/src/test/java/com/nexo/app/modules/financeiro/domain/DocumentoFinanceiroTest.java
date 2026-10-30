package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As invariantes do documento no repositório financeiro.
 *
 * <p>Duas ausências são deliberadas e estão cobertas aqui: o número do documento
 * <b>não</b> é obrigatório — recusar o arquivo por causa dele faria a pessoa
 * desistir de anexar — e a competência é deduzida em vez de exigida, porque é o
 * que a contabilidade faz na mão.</p>
 */
class DocumentoFinanceiroTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final String CHAVE_44 = "35240814200166000187550010000012341000012345";

    private DocumentoFinanceiro.Arquivo arquivo() {
        return new DocumentoFinanceiro.Arquivo("nota.pdf", "financeiro/documentos/nota.pdf",
                "application/pdf", 2048L, "abc123");
    }

    private DocumentoFinanceiro documento(DocumentoFinanceiro.Metadados metadados) {
        return new DocumentoFinanceiro(arquivo(), metadados, USUARIO);
    }

    private DocumentoFinanceiro notaFiscal() {
        return documento(DocumentoFinanceiro.Metadados.apenasTipo(TipoDocumentoFinanceiro.NOTA_FISCAL));
    }

    private DocumentoFinanceiro.Metadados comChave(String chaveAcesso) {
        return new DocumentoFinanceiro.Metadados(TipoDocumentoFinanceiro.NOTA_FISCAL, "1042", chaveAcesso,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    // ── Chave de acesso ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("A chave de acesso é guardada só com dígitos, colada com ou sem máscara")
    void chaveDeAcessoEhNormalizadaParaDigitos() {
        assertEquals(CHAVE_44, documento(comChave(CHAVE_44)).getChaveAcesso());

        String comMascara = "3524 0814 2001 6600 0187 5500 1000 0012 3410 0001 2345";
        assertEquals(CHAVE_44, documento(comChave(comMascara)).getChaveAcesso());
    }

    @Test
    @DisplayName("Chave com quantidade de dígitos diferente de 44 é recusada")
    void chaveDeAcessoComTamanhoErradoEhRecusada() {
        var erro = assertThrows(InvarianteVioladaException.class, () -> documento(comChave("3524081420")));
        assertTrue(erro.getMessage().contains("44"));
    }

    @Test
    @DisplayName("Sem chave informada, o campo simplesmente fica vazio")
    void chaveDeAcessoEhOpcional() {
        assertNull(documento(comChave("   ")).getChaveAcesso());
    }

    @Test
    @DisplayName("Codigo pagavel fica disponivel no detalhe do documento")
    void codigoPagamentoEhNormalizadoEIdentificado() {
        var metadados = new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.BOLETO, "77", null,
                "  00190.00009 01234.567890  ", null, null,
                null, null, null, null, null, null, null, null, null);

        var documento = documento(metadados);

        assertEquals("00190.00009 01234.567890", documento.getCodigoPagamento());
        assertTrue(documento.possuiCodigoPagamento());
    }

    // ── Datas e valor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("A competência é sempre normalizada para o dia 1 do mês")
    void competenciaEhNormalizadaParaOPrimeiroDia() {
        var documento = documento(new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.NOTA_FISCAL, "1042", null, null, null,
                null, null, LocalDate.of(2026, 8, 23), null, null, null, null, null, null));

        assertEquals(LocalDate.of(2026, 8, 1), documento.getCompetencia());
    }

    @Test
    @DisplayName("Vencimento anterior à emissão não descreve documento nenhum")
    void vencimentoAnteriorAEmissaoEhRecusado() {
        var erro = assertThrows(InvarianteVioladaException.class, () -> documento(
                new DocumentoFinanceiro.Metadados(TipoDocumentoFinanceiro.BOLETO, null, null, null, null,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 5), null, null,
                        null, null, null, null, null)));
        assertTrue(erro.getMessage().contains("vencimento"));
    }

    @Test
    @DisplayName("Valor informado precisa ser maior que zero")
    void valorNaoPositivoEhRecusado() {
        assertThrows(InvarianteVioladaException.class, () -> documento(
                new DocumentoFinanceiro.Metadados(TipoDocumentoFinanceiro.BOLETO, null, null, null, null,
                        null, null, null, BigDecimal.ZERO, null, null, null, null, null)));
    }

    @Test
    @DisplayName("A competência ausente é deduzida do vencimento e, na falta dele, da emissão")
    void competenciaEhDeduzidaNaOrdemVencimentoDepoisEmissao() {
        var comVencimento = documento(new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.BOLETO, null, null, null, null,
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 10), null, null,
                null, null, null, null, null));
        comVencimento.deduzirCompetenciaSeAusente();
        assertEquals(LocalDate.of(2026, 8, 1), comVencimento.getCompetencia());

        var apenasEmissao = documento(new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.NOTA_FISCAL, null, null, null, null,
                LocalDate.of(2026, 7, 28), null, null, null, null, null, null, null, null));
        apenasEmissao.deduzirCompetenciaSeAusente();
        assertEquals(LocalDate.of(2026, 7, 1), apenasEmissao.getCompetencia());
    }

    @Test
    @DisplayName("Competência informada não é sobrescrita pela dedução")
    void competenciaInformadaPrevaleceSobreADeducao() {
        var documento = documento(new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.NOTA_FISCAL, null, null, null, null,
                null, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 7, 1), null,
                null, null, null, null, null));
        documento.deduzirCompetenciaSeAusente();

        assertEquals(LocalDate.of(2026, 7, 1), documento.getCompetencia());
    }

    // ── Ciclo de conferência ────────────────────────────────────────────────────

    @Test
    @DisplayName("Arquivar sem conferir esconderia o documento sem que ninguém o olhasse")
    void arquivarExigeConferenciaPrevia() {
        var documento = notaFiscal();
        assertThrows(InvarianteVioladaException.class, documento::arquivar);

        documento.conferir(USUARIO, "Confere com o pedido");
        documento.arquivar();

        assertEquals(SituacaoDocumento.ARQUIVADO, documento.getSituacao());
    }

    @Test
    @DisplayName("Documento arquivado não aceita correção — reabra antes")
    void atualizarMetadadosEhRecusadoEmArquivado() {
        var documento = notaFiscal();
        documento.conferir(USUARIO, null);
        documento.arquivar();

        var erro = assertThrows(InvarianteVioladaException.class, () -> documento.atualizarMetadados(
                DocumentoFinanceiro.Metadados.apenasTipo(TipoDocumentoFinanceiro.RECIBO)));
        assertTrue(erro.getMessage().contains("arquivado"));

        documento.reabrir();
        documento.atualizarMetadados(DocumentoFinanceiro.Metadados.apenasTipo(TipoDocumentoFinanceiro.RECIBO));
        assertEquals(TipoDocumentoFinanceiro.RECIBO, documento.getTipo());
        assertEquals(SituacaoDocumento.CONFERIDO, documento.getSituacao());

        assertThrows(InvarianteVioladaException.class, documento::reabrir);
    }

    @Test
    @DisplayName("Apontar pendência sem dizer o que falta devolve o problema sem a informação")
    void apontarPendenciaExigeMotivo() {
        var documento = notaFiscal();

        assertThrows(InvarianteVioladaException.class, () -> documento.apontarPendencia(USUARIO, "  "));

        documento.apontarPendencia(USUARIO, "Falta o número da nota");
        assertEquals(SituacaoDocumento.COM_PENDENCIA, documento.getSituacao());
        assertEquals("Falta o número da nota", documento.getMotivoPendencia());
        assertTrue(documento.getSituacao().isEmAberto());
    }

    @Test
    @DisplayName("Conferir depois de uma pendência limpa o motivo que a originou")
    void conferirLimpaAPendenciaAnterior() {
        var documento = notaFiscal();
        documento.apontarPendencia(USUARIO, "Falta o número da nota");

        documento.conferir(USUARIO, "Número informado");

        assertEquals(SituacaoDocumento.CONFERIDO, documento.getSituacao());
        assertNull(documento.getMotivoPendencia());
        assertFalse(documento.getSituacao().isEmAberto());
    }

    // ── Leituras derivadas ──────────────────────────────────────────────────────

    @Test
    @DisplayName("A identificação pendente só é cobrada dos tipos que dependem do número")
    void identificacaoPendenteSegueOTipo() {
        assertTrue(notaFiscal().identificacaoPendente());
        assertTrue(documento(DocumentoFinanceiro.Metadados.apenasTipo(TipoDocumentoFinanceiro.BOLETO))
                .identificacaoPendente());
        assertFalse(documento(DocumentoFinanceiro.Metadados.apenasTipo(TipoDocumentoFinanceiro.FOTO))
                .identificacaoPendente());

        var comNumero = documento(new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.NOTA_FISCAL, "1042", null, null, null,
                null, null, null, null, null, null, null, null, null));
        assertFalse(comNumero.identificacaoPendente());
    }

    @Test
    @DisplayName("Vencido só cobra alguém enquanto o documento está em aberto")
    void vencidoDependeDaSituacaoEstarEmAberto() {
        var documento = documento(new DocumentoFinanceiro.Metadados(
                TipoDocumentoFinanceiro.BOLETO, "77", null, null, null,
                null, LocalDate.of(2026, 8, 1), null, null, null, null, null, null, null));

        assertTrue(documento.vencidoEm(LocalDate.of(2026, 8, 7)));

        documento.conferir(USUARIO, null);
        assertFalse(documento.vencidoEm(LocalDate.of(2026, 8, 7)));
    }

    @Test
    @DisplayName("getOrigem nunca retorna nulo mesmo se o campo estiver nulo após carga do Hibernate")
    void getOrigemNuncaRetornaNulo() throws Exception {
        var documento = notaFiscal();
        var campoOrigem = DocumentoFinanceiro.class.getDeclaredField("origem");
        campoOrigem.setAccessible(true);
        campoOrigem.set(documento, null);

        var origem = documento.getOrigem();
        org.junit.jupiter.api.Assertions.assertNotNull(origem);
        assertTrue(origem.vazia());
        assertNull(origem.getTipo());
        assertNull(origem.getSolicitacaoId());
        assertNull(origem.getNumero());
    }

    @Test
    @DisplayName("desvincular restaura a origem vazia")
    void desvincularRestauraOrigemVazia() {
        var documento = notaFiscal();
        documento.desvincular();

        assertTrue(documento.getOrigem().vazia());
        assertNull(documento.getOrigem().getTipo());
    }
}
