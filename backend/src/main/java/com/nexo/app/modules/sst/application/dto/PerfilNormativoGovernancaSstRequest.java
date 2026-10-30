package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoPerfilNormativoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PerfilNormativoGovernancaSstRequest(
        @NotBlank @Size(max = 60) String codigo,
        @NotNull @Min(1) Integer versaoPerfil,
        @NotNull EscopoPerfilNormativoSst escopo,
        @NotNull TipoCorpoGovernancaSst tipoCorpo,
        @Size(max = 20) String jurisdicao,
        @Size(max = 120) String orgaoEmissor,
        @Size(max = 180) String atoNormativo,
        @NotBlank @Size(max = 80) String edicao,
        @NotNull LocalDate inicioVigencia,
        LocalDate fimVigencia,
        @Size(max = 1000) String urlOficial,
        List<@Valid RegraPapelPerfilNormativoRequest> requisitosPapeis
) {
}
