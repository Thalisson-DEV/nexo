package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCatalogoSst;

import java.util.UUID;

/**
 * Entrada de catálogo de SST cadastrada.
 */
public record CatalogoSstResponse(
        UUID id,
        TipoCatalogoSst tipo,
        String nome,
        String descricao,
        String codigo,
        int ordem,
        boolean ativo
) {
}
