package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.util.List;

public record TreinamentoGovernancaSstFilter(
        List<TipoCorpoGovernancaSst> contexto,
        Boolean ativo,
        String buscaGlobal
) {
    public TreinamentoGovernancaSstFilter {
        contexto = SelecaoMultipla.normalizar(contexto);
    }
}
