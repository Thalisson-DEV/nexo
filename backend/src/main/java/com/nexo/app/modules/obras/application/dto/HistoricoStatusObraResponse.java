package com.nexo.app.modules.obras.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricoStatusObraResponse(
        UUID id,
        UUID statusAnteriorId,
        String statusAnteriorNome,
        UUID statusNovoId,
        String statusNovoNome,
        String observacao,
        String usuario,
        OffsetDateTime data
) {
}
