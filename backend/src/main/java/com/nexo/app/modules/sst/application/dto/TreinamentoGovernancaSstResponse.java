package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.util.Set;
import java.util.UUID;

public record TreinamentoGovernancaSstResponse(
        UUID id,
        String codigo,
        String nome,
        String descricao,
        Set<TipoCorpoGovernancaSst> contextos,
        boolean ativo,
        Long versao
) {
}
