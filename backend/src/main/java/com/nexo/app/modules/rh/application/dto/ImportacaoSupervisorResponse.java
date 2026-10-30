package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ImportacaoSupervisorResponse(
    List<SupervisorImportacaoDTO> registros,
    int totalRegistros,
    int totalSucessos,
    int totalFalhas
) {}
