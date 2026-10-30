package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.shared.domain.UnidadeFederativa;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CorpoGovernancaSstUpdateRequest(
        @NotNull Long versao,
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Size(max = 180) String unidadeGovernanca,
        @NotBlank @Pattern(regexp = UnidadeFederativa.REGEX,
                message = "Informe uma UF brasileira válida") String uf,
        @NotNull LocalDate inicioPeriodo,
        @NotNull LocalDate fimPeriodo,
        @NotNull @Min(1) @Max(4) Integer grauRisco,
        UUID idPerfilNormativo,
        @NotNull Boolean hipoteseRepresentanteNomeadoConfirmada
) {
}
