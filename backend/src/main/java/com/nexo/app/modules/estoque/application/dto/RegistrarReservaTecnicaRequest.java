package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record RegistrarReservaTecnicaRequest(
        @NotNull Long versaoEsperada,
        @NotBlank @Pattern(regexp = "[0-9]+") @Size(max = 20) String numero,
        @NotNull LocalDate dataCriacao,
        UUID idAgenteResponsavel,
        @Size(max = 500) String justificativa
) {
}
