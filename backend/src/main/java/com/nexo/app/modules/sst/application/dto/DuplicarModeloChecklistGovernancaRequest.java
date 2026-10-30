package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DuplicarModeloChecklistGovernancaRequest(
        @NotBlank @Size(max = 180) String nome,
        @Size(max = 1000) String orientacao,
        @Size(max = 500) String referenciaNormativa) {
}
