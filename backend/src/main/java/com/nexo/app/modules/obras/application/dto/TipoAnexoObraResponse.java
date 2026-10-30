package com.nexo.app.modules.obras.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TipoAnexoObraResponse(
        UUID id,
        String nome,
        String descricao,
        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao
) {
}
