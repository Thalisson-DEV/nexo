package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoTipoEquipeResponse(
    List<TipoEquipeImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
