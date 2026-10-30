package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoFuncaoResponse(
    List<FuncaoImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
