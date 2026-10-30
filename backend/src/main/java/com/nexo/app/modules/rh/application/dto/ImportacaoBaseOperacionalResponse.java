package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoBaseOperacionalResponse(
    List<BaseOperacionalImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
