package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Volume histórico de desvios apontados em um balde da série gerencial.
 *
 * @param chave identificador estável do balde (ex.: "2026-08-03", "2026-08", "2026")
 * @param rotulo texto pronto para o eixo do gráfico (ex.: "03/08", "ago/2026", "2026")
 * @param inicio primeiro dia do balde
 * @param totalDesvios desvios apontados nas inspeções do balde, em qualquer status
 */
public record TendenciaDesviosItem(
        String chave,
        String rotulo,
        LocalDate inicio,

        @Schema(description = "Desvios apontados no balde, incluindo abertos, em tratamento e resolvidos")
        long totalDesvios
) {
}
