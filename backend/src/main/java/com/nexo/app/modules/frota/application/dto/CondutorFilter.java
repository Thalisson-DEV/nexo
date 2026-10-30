package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.CategoriaCnh;
import com.nexo.app.modules.frota.domain.SituacaoVencimento;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.util.List;
import java.util.UUID;

/** Filtro de condutores. {@code busca} procura no nome (RH) ou no número completo da CNH. */
public record CondutorFilter(
        String busca,
        List<UUID> idsBase,
        List<CategoriaCnh> categorias,
        SituacaoVencimento situacaoCnh,
        Boolean ativo
) {
    public CondutorFilter {
        idsBase = SelecaoMultipla.normalizar(idsBase);
        categorias = SelecaoMultipla.normalizar(categorias);
    }
}
