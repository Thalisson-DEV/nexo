package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ModeloChecklistGovernancaRequest(
        @NotNull UUID idFormulario,
        @NotNull TipoCorpoGovernancaSst tipoCorpo,
        @NotBlank @Size(max = 180) String nome,
        @Size(max = 1000) String orientacao,
        @Size(max = 500) String referenciaNormativa,
        Long versaoEsperada) {
}
