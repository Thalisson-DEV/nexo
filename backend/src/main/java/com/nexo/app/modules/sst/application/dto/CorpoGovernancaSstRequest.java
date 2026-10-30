package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.RegimeComposicaoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.domain.UnidadeFederativa;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CorpoGovernancaSstRequest(
        @NotNull TipoCorpoGovernancaSst tipo,
        RegimeComposicaoSst regimeComposicao,
        @NotBlank @Size(max = 150) String nome,
        @NotNull UUID idBaseOperacional,
        @NotBlank @Size(max = 180) String unidadeGovernanca,
        @NotBlank @Pattern(regexp = UnidadeFederativa.REGEX,
                message = "Informe uma UF brasileira válida") String uf,
        @NotNull LocalDate inicioPeriodo,
        @NotNull LocalDate fimPeriodo,
        @NotNull @Min(1) @Max(4) Integer grauRisco,
        UUID idPerfilNormativo,
        @NotNull Boolean hipoteseRepresentanteNomeadoConfirmada,
        List<UUID> idsBasesAbrangidas
) {
}
