package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Cartões de topo do dashboard de inspeções: números diretos, sem fórmula escondida.
 *
 * @param variacaoPercentual variação do total em relação ao período anterior de mesmo tamanho
 * @param percentualSemDesvio inspeções sem nenhum desvio / total (taxa de conformidade)
 */
public record ResumoInspecoesDashboard(
        long totalInspecoes,
        long totalPeriodoAnterior,
        BigDecimal variacaoPercentual,

        long inspecoesComDesvio,
        long inspecoesSemDesvio,
        BigDecimal percentualSemDesvio,

        long totalDesvios,
        BigDecimal mediaDesviosPorInspecao,

        long colaboradoresInspecionados,
        BigDecimal mediaColaboradoresPorInspecao,

        long inspetoresAtivos,
        BigDecimal mediaInspecoesPorInspetor,

        @Schema(description = "Inspeções por dia corrido do período")
        BigDecimal mediaInspecoesPorDia
) {
}
