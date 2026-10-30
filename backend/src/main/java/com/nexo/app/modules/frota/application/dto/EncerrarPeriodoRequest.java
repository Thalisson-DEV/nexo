package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record EncerrarPeriodoRequest(
        @NotNull(message = "Informe a data e hora de encerramento.") OffsetDateTime fim
) {
}
