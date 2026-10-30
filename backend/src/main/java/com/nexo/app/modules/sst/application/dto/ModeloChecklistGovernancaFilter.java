package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.util.List;

public record ModeloChecklistGovernancaFilter(
        String buscaGlobal,
        List<TipoCorpoGovernancaSst> tiposCorpo,
        Boolean ativo) {
}
