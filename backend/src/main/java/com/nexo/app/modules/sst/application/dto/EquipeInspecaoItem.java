package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Situação de uma equipe no período: volume inspecionado, o que foi encontrado e
 * há quanto tempo ela não recebe uma inspeção.
 *
 * @param desviosPorInspecao  desvios encontrados / inspeções realizadas na equipe
 * @param percentualSemDesvio inspeções sem nenhum desvio / inspeções da equipe (conformidade)
 * @param diasSemInspecao     dias entre a última inspeção da equipe e o fim do período
 */
public record EquipeInspecaoItem(
        UUID idEquipe,
        String nomeEquipe,

        long inspecoes,
        BigDecimal percentualDoTotal,

        long desvios,
        BigDecimal desviosPorInspecao,
        BigDecimal percentualSemDesvio,

        @Schema(description = "Colaboradores distintos inspecionados na equipe")
        long colaboradoresInspecionados,

        LocalDate ultimaInspecao,
        long diasSemInspecao
) {
}
