package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConcluirAcaoGovernancaRequest(
        @NotBlank @Size(max = 2000) String resultado,
        @NotNull Long versaoEsperada) {
}
