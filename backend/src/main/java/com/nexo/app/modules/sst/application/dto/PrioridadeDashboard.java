package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;

import java.util.Map;

public record PrioridadeDashboard(
        String tipo,
        GravidadeDesvio severidade,
        String titulo,
        String evidencia,
        String acaoSugerida,
        Map<String, Object> drilldown
) {}
