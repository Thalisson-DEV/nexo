package com.nexo.app.modules.chamado.application.dto;

import java.util.Map;

public record PrioridadeChamadoDashboard(
        String tipo,
        String severidade,
        String titulo,
        String evidencia,
        String acaoSugerida,
        Map<String, Object> drilldown
) {}
