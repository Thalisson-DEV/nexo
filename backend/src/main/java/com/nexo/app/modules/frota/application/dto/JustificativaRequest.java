package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JustificativaRequest(
        @NotBlank(message = "Informe a justificativa.") @Size(min = 5, max = 400) String justificativa
) {
}
