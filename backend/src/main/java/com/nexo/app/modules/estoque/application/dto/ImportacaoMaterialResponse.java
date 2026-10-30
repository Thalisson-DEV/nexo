package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ImportacaoMaterialResponse(
    List<MaterialImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
