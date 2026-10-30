package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EncerrarSaldoTecnicoRequest(
        @NotNull Long versaoEsperada,
        @NotBlank @Size(max = 500) String motivo
) {
}
