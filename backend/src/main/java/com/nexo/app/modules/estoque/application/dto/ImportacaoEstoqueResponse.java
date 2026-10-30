package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ImportacaoEstoqueResponse(
    List<EstoqueImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
