package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Exposição de um colaborador às inspeções no período: quantas vezes foi inspecionado,
 * quantos desvios lhe foram atribuídos e quando foi a última vez.
 */
public record ColaboradorInspecionadoItem(
        UUID idColaborador,
        String nomeColaborador,

        long vezesInspecionado,

        @Schema(description = "Desvios atribuídos ao colaborador nas inspeções do período")
        long desviosRecebidos,

        LocalDate ultimaInspecao,
        long diasDesdeUltimaInspecao
) {
}
