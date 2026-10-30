package com.nexo.app.modules.frota.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AcoplamentoImplementoResponse(
        UUID id,
        UUID idImplemento,
        String identificacao,
        String tipoImplemento,
        UUID idVeiculo,
        String placa,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String observacao
) {
}
