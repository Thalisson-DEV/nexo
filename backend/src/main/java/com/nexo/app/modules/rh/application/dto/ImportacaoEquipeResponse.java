package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoEquipeResponse(
    List<EquipeImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
