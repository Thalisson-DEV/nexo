package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Há quantos dias uma base operacional não registra evento com lesão.
 *
 * <p>Diferente dos demais indicadores, este <b>ignora o período consultado</b>: a
 * pergunta "há quanto tempo não acontece" só faz sentido contra a última ocorrência
 * de fato, venha ela de quando vier. Base sem nenhum evento com lesão aparece com
 * {@code ultimaOcorrencia} nula.</p>
 */
public record DiasSemAcidenteItem(
        UUID idBaseOperacional,
        String baseNome,

        @Schema(description = "Última ocorrência com lesão na base, de toda a série histórica")
        OffsetDateTime ultimaOcorrencia,

        @Schema(description = "Nulo quando a base nunca registrou evento com lesão")
        Long diasSemAcidente,

        @Schema(description = "Eventos com lesão da base dentro do período consultado")
        long comLesaoNoPeriodo
) {
}
