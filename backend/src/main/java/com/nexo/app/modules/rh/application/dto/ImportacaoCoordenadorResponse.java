package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoCoordenadorResponse(
    List<CoordenadorImportacaoDTO> registros,
    int totalRegistros,
    int totalSucessos,
    int totalFalhas
) {}
