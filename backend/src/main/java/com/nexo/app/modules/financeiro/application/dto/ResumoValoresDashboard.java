package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * O dinheiro do período em cada estágio, e o que se perde entre eles.
 *
 * <p>O <b>corte na aprovação</b> tem nome próprio porque é decidido por alguém: é
 * a diferença entre o que se pediu e o que se autorizou, nas solicitações que
 * foram aprovadas. Ele não se confunde com o valor rejeitado, que é o pedido
 * negado por inteiro — somar os dois num "valor não autorizado" apagaria a
 * diferença entre negociar e recusar.</p>
 *
 * <p>{@code valorVencido} e {@code valorAVencer} olham apenas o que já foi
 * autorizado: cobrar vencimento de uma solicitação que ninguém aprovou seria
 * cobrar a data errada da pessoa errada.</p>
 */
public record ResumoValoresDashboard(
        BigDecimal valorSolicitado,
        BigDecimal valorAprovado,
        BigDecimal valorAprovadoPeriodoAnterior,
        BigDecimal variacaoValorAprovado,

        @Schema(description = "Somatório do que foi pedido nas solicitações rejeitadas")
        BigDecimal valorRejeitado,

        @Schema(description = "Somatório do que foi pedido nas solicitações canceladas")
        BigDecimal valorCancelado,

        @Schema(description = "Pedido em rascunho ou na fila de decisão — dinheiro ainda indefinido")
        BigDecimal valorEmAnalise,

        @Schema(description = "Solicitado menos aprovado, nas que foram aprovadas")
        BigDecimal corteNaAprovacao,
        BigDecimal percentualCorte,

        @Schema(description = "Valor aprovado sobre o valor solicitado no período")
        BigDecimal taxaAutorizacao,

        BigDecimal ticketMedioSolicitado,
        BigDecimal ticketMedioAprovado,
        BigDecimal maiorValorSolicitado,

        BigDecimal valorAprovadoCompras,
        BigDecimal valorAprovadoPagamentos,

        @Schema(description = "Aprovado com vencimento já passado e sem comprovante de pagamento anexado")
        BigDecimal valorVencido,
        long quantidadeVencida,

        @Schema(description = "Aprovado com vencimento nos próximos 30 dias")
        BigDecimal valorAVencer,
        long quantidadeAVencer
) {
}
