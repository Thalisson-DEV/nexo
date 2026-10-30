package com.nexo.app.modules.obras.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StatusObraResponse(
        UUID id,
        String nome,
        Integer ordem,
        boolean concluida,
        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao
) {
}
