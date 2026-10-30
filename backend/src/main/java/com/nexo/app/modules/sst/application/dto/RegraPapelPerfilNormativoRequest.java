package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RegraPapelPerfilNormativoRequest(
        @NotNull PapelMembroGovernancaSst papel,
        @NotNull @Min(1) Integer quantidadeMinima
) {
}
