package com.nexo.app.modules.contrato.application.dto;

import java.util.List;

public record ImportacaoAcaoResponse(
    List<AcaoImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
