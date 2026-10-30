package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Relato do que foi feito a respeito do evento.
 *
 * <p>Opcional ao iniciar a tratativa (assumir a análise já é uma informação) e
 * obrigatório na conclusão — concluir sem relato é arquivar, e arquivar não é tratar.</p>
 */
public record TratativaEventoRequest(

        @Schema(description = "Omitido na conclusão, mantém a tratativa já registrada")
        @Size(max = 4000)
        String tratativa
) {
}
