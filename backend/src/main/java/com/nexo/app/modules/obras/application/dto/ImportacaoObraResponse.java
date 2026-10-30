package com.nexo.app.modules.obras.application.dto;

import java.util.List;

public record ImportacaoObraResponse(
        List<ObraImportacaoDTO> registros,
        int totalProcessados,
        int totalSucesso,
        int totalFalha
) {
}
