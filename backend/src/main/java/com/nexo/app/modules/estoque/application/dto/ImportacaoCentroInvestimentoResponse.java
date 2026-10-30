package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ImportacaoCentroInvestimentoResponse(
    List<CentroInvestimentoImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
