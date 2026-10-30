package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ponto da série temporal do dashboard: quanto foi realizado e quanto era esperado
 * naquele recorte. Os baldes vazios também vêm preenchidos, para o gráfico não ter buracos.
 *
 * @param chave  identificador estável do balde (ex.: "2026-08-03", "2026-08", "2026")
 * @param rotulo texto pronto para o eixo do gráfico (ex.: "03/08", "ago/2026", "2026")
 */
public record SerieMetaItem(
        String chave,
        String rotulo,
        LocalDate inicio,

        long realizado,
        BigDecimal meta,
        BigDecimal percentualAtingido,

        @Schema(description = "Desvios registrados nas inspeções do balde")
        long desvios
) {
}
