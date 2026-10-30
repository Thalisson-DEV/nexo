package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoMunicipioResponse(
    List<MunicipioImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
