package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Lançamento manual de KM pela web. */
public record RegistrarLeituraRequest(
        @NotNull(message = "Informe a quilometragem.") @Min(0) @Max(9_999_999) Long km,
        @NotNull(message = "Informe a data e hora da leitura.") OffsetDateTime lidoEm
) {
}
