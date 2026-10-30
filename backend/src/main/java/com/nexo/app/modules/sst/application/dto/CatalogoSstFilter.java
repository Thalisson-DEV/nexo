package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import io.swagger.v3.oas.annotations.Parameter;

/**
 * Filtro dinâmico para listagem das entradas de catálogo de SST.
 */
public record CatalogoSstFilter(

        @Parameter(description = "Vocabulário a listar. Omitido, devolve todos os tipos.")
        TipoCatalogoSst tipo,

        Boolean ativo,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por nome, descrição e código")
        String buscaGlobal
) {
}
