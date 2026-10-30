package com.nexo.app.modules.sst.application.dto;

import java.util.List;

public record ImportacaoEventoSstResponse(
        List<EventoSstImportacaoDTO> registros,
        int totalProcessados,
        int totalSucesso,
        int totalFalha
) {
}
