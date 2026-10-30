package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record InclusaoMembroCorpoSstRequest(
        @NotNull Long versao,
        @NotNull @Valid MembroCorpoSstRequest membro
) {
}
