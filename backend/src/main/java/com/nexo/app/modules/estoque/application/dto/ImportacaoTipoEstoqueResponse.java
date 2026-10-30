package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ImportacaoTipoEstoqueResponse(
    List<TipoEstoqueImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
