package com.nexo.app.shared.application.analytics;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record SemanticQueryResult(
        String dataset,
        String datasetVersion,
        List<Map<String, Object>> linhas,
        int quantidadeLinhas,
        boolean truncado,
        String timezone,
        Map<String, Object> consultaEfetiva,
        OffsetDateTime calculadoEm) {
}
