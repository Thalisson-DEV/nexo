package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo de um cancelamento ou de uma reabertura.
 *
 * <p>É exigido porque as duas operações mexem no que os indicadores contam: um evento
 * cancelado sai da acidentalidade e um reaberto volta. Sem o motivo gravado, a série
 * histórica muda sem explicação.</p>
 */
public record MotivoEventoRequest(

        @NotBlank(message = "Informe o motivo")
        @Size(max = 500)
        String motivo
) {
}
