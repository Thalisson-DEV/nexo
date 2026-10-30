package com.nexo.app.modules.contrato.application.dto;

import java.util.List;

public record ImportacaoContratoResponse(
    List<ContratoImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
