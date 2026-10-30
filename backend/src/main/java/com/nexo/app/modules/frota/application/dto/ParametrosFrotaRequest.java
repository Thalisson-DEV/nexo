package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ParametrosFrotaRequest(
        @NotEmpty(message = "Informe ao menos uma faixa de aviso.") @Size(max = 10) List<@NotNull @Min(0) @Max(365) Integer> faixasAlertaDias,
        @NotNull(message = "Informe o limite de km por dia.") @Min(1) @Max(5000) Integer limiteKmDia
) {
}
