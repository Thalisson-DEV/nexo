package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Cartões de volume: quantos termos existem, em que etapa pararam e quanta gente
 * eles alcançam. É a leitura de fluxo — o dinheiro fica em
 * {@link ResumoFinanceiroDashboard}.
 */
public record ResumoDescontosDashboard(
        long totalDescontos,
        long totalPeriodoAnterior,

        @Schema(description = "Variação do volume sobre o período anterior; pode ser negativa")
        BigDecimal variacaoPercentual,

        long solicitados,
        long validados,
        long baixados,
        long rejeitados,

        @Schema(description = "SOLICITADO ou VALIDADO — ainda consomem trabalho de alguém")
        long pendentes,
        BigDecimal percentualPendentes,

        @Schema(description = "BAIXADO ou REJEITADO — já têm desfecho")
        long finalizados,

        @Schema(description = "Baixados sobre finalizados. Sem nenhum desfecho no período, retorna 0")
        BigDecimal taxaAprovacao,
        BigDecimal taxaRejeicao,

        @Schema(description = "Colaboradores distintos com ao menos um desconto no período")
        long colaboradoresAtingidos,

        @Schema(description = "Efetivo ativo no recorte — denominador da cobertura")
        long colaboradoresAtivos,
        BigDecimal percentualDoEfetivo,

        @Schema(description = "Colaboradores com 2 ou mais descontos no período")
        long colaboradoresReincidentes,
        BigDecimal mediaDescontosPorColaborador,

        long totalItens,
        BigDecimal mediaItensPorDesconto,
        BigDecimal mediaParcelas
) {
}
