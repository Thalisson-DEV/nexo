package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record TreinamentoGovernancaSstRequest(
        @NotBlank @Size(max = 80) String codigo,
        @NotBlank @Size(max = 160) String nome,
        @Size(max = 500) String descricao,
        @NotNull Set<TipoCorpoGovernancaSst> contextos
) {
}
