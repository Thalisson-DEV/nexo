package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * O repositório documental visto de cima: o que chegou, o que a contabilidade
 * ainda não olhou e quanto da despesa autorizada tem lastro fiscal.
 *
 * <p>{@code coberturaFiscal} é o indicador que responde a pergunta que a
 * contabilidade faz de verdade — "de tudo que foi aprovado no período, quanto tem
 * nota fiscal ou recibo anexado?". Ele conta apenas as solicitações
 * <b>aprovadas</b>: cobrar documento fiscal de um pedido em rascunho é cobrar
 * antes da hora, e inflaria o problema sem apontar nada acionável.</p>
 *
 * <p>{@code duplicados} sai da comparação de hash do conteúdo. Não é arrumação de
 * arquivo: a mesma nota entrando duas vezes por caminhos diferentes é o começo de
 * um pagamento em duplicidade.</p>
 */
public record ConformidadeDocumentalDashboard(
        long documentosRecebidos,
        long documentosPeriodoAnterior,
        BigDecimal variacaoDocumentos,

        BigDecimal valorDocumentado,

        // ── Fila de conferência (todo o histórico em aberto, não só o período) ──
        @Schema(description = "Aguardando a primeira conferência da contabilidade")
        long aguardandoConferencia,

        @Schema(description = "Conferidos e recusados; a ação voltou para quem enviou")
        long comPendencia,

        long conferidos,
        long arquivados,

        @Schema(description = "Conferidos e arquivados sobre tudo que já entrou no repositório")
        BigDecimal taxaConferencia,

        @Schema(description = "Idade média da fila de conferência, desde a última movimentação")
        BigDecimal idadeMediaFilaDias,

        @Schema(description = "Idade do documento mais antigo parado em aberto")
        long idadeMaximaFilaDias,

        // ── Conformidade fiscal ────────────────────────────────────────────────
        @Schema(description = "Solicitações aprovadas no período com nota, boleto ou recibo anexado")
        long aprovadasComDocumentoFiscal,

        long aprovadasSemDocumentoFiscal,

        @Schema(description = "Percentual das aprovadas do período que têm documento fiscal")
        BigDecimal coberturaFiscal,

        @Schema(description = "Valor aprovado sem nenhum documento fiscal no repositório")
        BigDecimal valorSemDocumentoFiscal,

        // ── Higiene do repositório ─────────────────────────────────────────────
        @Schema(description = "Notas e boletos sem número informado — não servem como comprovação")
        long semIdentificacao,

        @Schema(description = "Documentos cujo conteúdo é idêntico ao de outro documento ativo")
        long duplicados,

        @Schema(description = "Documentos vencidos e ainda em aberto na conferência")
        long vencidosEmAberto,
        BigDecimal valorVencidoEmAberto,

        @Schema(description = "Documentos no repositório que ainda não foram ligados a nenhuma solicitação")
        long avulsosSemVinculo
) {
}
