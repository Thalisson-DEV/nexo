package com.nexo.app.shared.application.analytics;

import java.util.List;
import java.util.Set;

public record SemanticDatasetDescriptor(
        String id,
        String version,
        String nome,
        String descricao,
        String autoridadeObrigatoria,
        boolean permiteDetalhes,
        int limiteMaximoLinhas,
        int periodoMaximoDias,
        List<SemanticDimension> dimensoes,
        List<SemanticMeasure> medidas,
        List<String> exemplosPerguntas) {

    public record SemanticDimension(
            String id,
            String descricao,
            SemanticDataType tipo,
            boolean agrupavel,
            boolean sensivel,
            Set<SemanticFilterOperator> operadores) {
    }

    public record SemanticMeasure(
            String id,
            String descricao,
            SemanticDataType tipoResultado,
            String definicao) {
    }
}
