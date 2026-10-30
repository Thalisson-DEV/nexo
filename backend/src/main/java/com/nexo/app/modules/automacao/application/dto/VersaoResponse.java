package com.nexo.app.modules.automacao.application.dto;

import java.time.OffsetDateTime;

/** Uma versão salva da definição. */
public record VersaoResponse(
        int numero,
        OffsetDateTime data,
        String autor,
        String nota
) {
}
