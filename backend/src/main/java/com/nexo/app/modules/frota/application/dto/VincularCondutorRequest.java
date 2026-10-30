package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Coloca o colaborador como condutor do veículo a partir de {@code inicio}. Encerra, no
 * mesmo instante, o vínculo anterior do veículo e o do condutor com outro veículo.
 */
public record VincularCondutorRequest(
        @NotNull(message = "Informe o condutor.") UUID idColaborador,
        @NotNull(message = "Informe a data e hora de início.") OffsetDateTime inicio,
        @Size(max = 500) String observacao
) {
}
