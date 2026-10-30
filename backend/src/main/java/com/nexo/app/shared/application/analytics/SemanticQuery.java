package com.nexo.app.shared.application.analytics;

import java.util.List;

public record SemanticQuery(
        String dataset,
        List<String> dimensoes,
        List<String> medidas,
        List<SemanticFilter> filtros,
        List<String> agruparPor,
        List<SemanticOrder> ordenarPor,
        Integer limite) {

    public SemanticQuery {
        dimensoes = dimensoes == null ? List.of() : List.copyOf(dimensoes);
        medidas = medidas == null ? List.of() : List.copyOf(medidas);
        filtros = filtros == null ? List.of() : List.copyOf(filtros);
        agruparPor = agruparPor == null ? List.of() : List.copyOf(agruparPor);
        ordenarPor = ordenarPor == null ? List.of() : List.copyOf(ordenarPor);
    }

    public record SemanticFilter(String campo, SemanticFilterOperator operador, List<String> valores) {
        public SemanticFilter {
            valores = valores == null ? List.of() : List.copyOf(valores);
        }
    }

    public record SemanticOrder(String campo, Direction direcao) {
        public enum Direction { ASC, DESC }
    }
}
