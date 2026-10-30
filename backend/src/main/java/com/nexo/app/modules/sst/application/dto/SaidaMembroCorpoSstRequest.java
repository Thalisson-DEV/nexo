package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SaidaMembroCorpoSstRequest(
        @NotNull Long versao,
        @NotNull LocalDate dataEfeito,
        @NotBlank @Size(max = 500) String motivo
) {
}
