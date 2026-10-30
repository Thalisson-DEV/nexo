package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelarAcaoGovernancaRequest(
        @NotBlank @Size(max = 1000) String motivo,
        @NotNull Long versaoEsperada) {
}
