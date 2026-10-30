package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoColaboradorResponse(
    List<ColaboradorImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
