package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Volume do período: quantas solicitações, em que estado e de quem.
 *
 * <p>{@code decididas} é o denominador honesto das taxas de aprovação: contar o
 * rascunho e a solicitação ainda na fila junto com as decididas faria a taxa de
 * aprovação cair sempre que alguém abrisse pedidos novos, o que não diz nada
 * sobre a qualidade das decisões.</p>
 */
public record ResumoSolicitacoesDashboard(
        long total,
        long totalPeriodoAnterior,

        @Schema(description = "Pode ser negativa")
        BigDecimal variacaoPercentual,

        long compras,
        long pagamentos,

        long rascunhos,

        @Schema(description = "Submetidas e aguardando decisão")
        long aguardandoDecisao,
        long aprovadas,
        long rejeitadas,
        long canceladas,

        @Schema(description = "Rascunho ou aguardando decisão — ainda sem desfecho")
        long pendentes,
        BigDecimal percentualPendentes,

        @Schema(description = "Aprovadas mais rejeitadas: as que passaram por decisão")
        long decididas,
        BigDecimal taxaAprovacao,
        BigDecimal taxaRejeicao,

        @Schema(description = "Canceladas sobre o total aberto — desistência antes da decisão")
        BigDecimal taxaCancelamento,

        long solicitantesDistintos,

        @Schema(description = "Fornecedores cadastrados distintos utilizados no período")
        long fornecedoresDistintos,

        @Schema(description = "Pagamentos que apontam para uma compra aprovada")
        long pagamentosComCompraPrevia,

        @Schema(description = "Percentual dos pagamentos do período que vieram de uma compra")
        BigDecimal percentualComCompraPrevia
) {
}
