package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoResultadoResponse(
    int totalProcessados,
    int totalSucesso,
    int totalFalha,
    List<ImportacaoErro> erros
) {
}
