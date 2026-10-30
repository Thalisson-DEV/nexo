package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Cartão de cobertura: quanto do efetivo foi alcançado pelas inspeções do período.
 *
 * @param colaboradoresAtivos     efetivo ativo considerado (respeita o filtro de base operacional)
 * @param percentualCobertura     colaboradores inspecionados / efetivo ativo
 * @param colaboradoresSemInspecao efetivo que não foi inspecionado nenhuma vez no período
 * @param colaboradoresReincidentes colaboradores com dois ou mais desvios atribuídos no período
 */
public record ResumoColaboradoresDashboard(
        long colaboradoresInspecionados,
        long colaboradoresAtivos,
        BigDecimal percentualCobertura,
        long colaboradoresSemInspecao,

        @Schema(description = "Quantas vezes, em média, cada colaborador inspecionado foi alcançado")
        BigDecimal mediaInspecoesPorColaborador,

        long colaboradoresComDesvio,
        BigDecimal percentualComDesvio,
        long colaboradoresReincidentes
) {
}
