package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModeloChecklistGovernancaResponse(
        UUID id,
        UUID idFormulario,
        String formulario,
        TipoCorpoGovernancaSst tipoCorpo,
        String nome,
        String orientacao,
        String referenciaNormativa,
        boolean ativo,
        boolean utilizado,
        Long versao,
        OffsetDateTime dataCriacao) {
}
