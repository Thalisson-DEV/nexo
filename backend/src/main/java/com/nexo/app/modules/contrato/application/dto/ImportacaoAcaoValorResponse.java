package com.nexo.app.modules.contrato.application.dto;

import java.util.List;

public record ImportacaoAcaoValorResponse(
    List<AcaoValorImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
