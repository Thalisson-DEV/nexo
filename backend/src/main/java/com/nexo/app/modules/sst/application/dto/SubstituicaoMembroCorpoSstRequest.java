package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SubstituicaoMembroCorpoSstRequest(
        @NotNull Long versao,
        @NotNull LocalDate dataEfeito,
        @NotBlank @Size(max = 500) String motivoSaida,
        @NotBlank @Size(max = 500) String fundamentoProcedimental,
        @NotNull @Valid MembroCorpoSstRequest sucessor
) {
}
