package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoVeiculoResponse(
    List<VeiculoImportacaoDTO> registros,
    int totalRegistros,
    int totalSucessos,
    int totalFalhas
) {}
