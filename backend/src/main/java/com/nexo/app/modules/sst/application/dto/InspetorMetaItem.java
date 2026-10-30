package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Desempenho de um inspetor no período, comparado à meta dele.
 *
 * <p>Entram tanto inspetores com meta que não lançaram nada (realizado zero, útil para
 * cobrança) quanto quem lançou inspeções sem ter meta definida (meta zero).</p>
 */
public record InspetorMetaItem(
        UUID idInspetor,
        String nomeInspetor,

        long realizado,
        BigDecimal meta,

        @Schema(description = "Nulo quando o inspetor não tem meta no período")
        BigDecimal percentualAtingido,

        boolean atingiuMeta,

        long desvios,

        @Schema(description = "Participação do inspetor no total de inspeções do período")
        BigDecimal percentualDoTotal
) {
}
