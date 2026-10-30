package com.nexo.app.shared.application.analytics;

import java.util.HashSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SemanticQueryValidator {

    private static final int MAX_FILTERS = 20;
    private static final int MAX_FIELDS = 12;

    private SemanticQueryValidator() {
    }

    public static int validateAndResolveLimit(SemanticDatasetDescriptor descriptor, SemanticQuery query) {
        if (query == null || !descriptor.id().equals(query.dataset())) {
            throw new IllegalArgumentException("Dataset ausente ou diferente do descritor selecionado");
        }
        if (query.filtros().size() > MAX_FILTERS
                || query.dimensoes().size() + query.medidas().size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Consulta excede a complexidade permitida");
        }

        Map<String, SemanticDatasetDescriptor.SemanticDimension> dimensions = descriptor.dimensoes().stream()
                .collect(Collectors.toMap(SemanticDatasetDescriptor.SemanticDimension::id, Function.identity()));
        Set<String> measures = descriptor.medidas().stream()
                .map(SemanticDatasetDescriptor.SemanticMeasure::id)
                .collect(Collectors.toSet());

        requireKnown(query.dimensoes(), dimensions.keySet(), "dimensao");
        requireKnown(query.agruparPor(), dimensions.keySet(), "agrupamento");
        requireKnown(query.medidas(), measures, "medida");

        query.agruparPor().forEach(field -> {
            if (!dimensions.get(field).agrupavel()) {
                throw new IllegalArgumentException("Dimensao nao agrupavel: " + field);
            }
        });
        query.filtros().forEach(filter -> {
            SemanticDatasetDescriptor.SemanticDimension dimension = dimensions.get(filter.campo());
            if (dimension == null || !dimension.operadores().contains(filter.operador())) {
                throw new IllegalArgumentException("Filtro nao suportado: " + filter.campo());
            }
            validateValueCount(filter);
        });
        validateTemporalWindow(descriptor, query, dimensions);

        if (!query.medidas().isEmpty() && !new HashSet<>(query.dimensoes()).equals(new HashSet<>(query.agruparPor()))) {
            throw new IllegalArgumentException("Em agregacoes, dimensoes selecionadas devem corresponder ao agrupamento");
        }
        if (query.medidas().isEmpty() && !query.agruparPor().isEmpty()) {
            throw new IllegalArgumentException("Agrupamento requer ao menos uma medida");
        }
        if (query.medidas().isEmpty() && !descriptor.permiteDetalhes()) {
            throw new IllegalArgumentException("O dataset nao permite consulta detalhada");
        }
        if (query.dimensoes().isEmpty() && query.medidas().isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos uma dimensao ou medida");
        }

        Set<String> selected = new HashSet<>(query.dimensoes());
        selected.addAll(query.medidas());
        query.ordenarPor().forEach(order -> {
            if (!selected.contains(order.campo())) {
                throw new IllegalArgumentException("Ordenacao deve usar um campo selecionado: " + order.campo());
            }
        });

        int requested = query.limite() == null ? Math.min(50, descriptor.limiteMaximoLinhas()) : query.limite();
        if (requested < 1) {
            throw new IllegalArgumentException("Limite deve ser positivo");
        }
        return Math.min(requested, descriptor.limiteMaximoLinhas());
    }

    private static void requireKnown(Iterable<String> requested, Set<String> known, String type) {
        for (String field : requested) {
            if (field == null || !field.matches("[a-z][a-z0-9_]*") || !known.contains(field)) {
                throw new IllegalArgumentException(type + " desconhecida: " + field);
            }
        }
    }

    private static void validateValueCount(SemanticQuery.SemanticFilter filter) {
        int count = filter.valores().size();
        switch (filter.operador()) {
            case IS_NULL, IS_NOT_NULL -> {
                if (count != 0) throw new IllegalArgumentException("Operador nao aceita valores");
            }
            case BETWEEN -> {
                if (count != 2) throw new IllegalArgumentException("BETWEEN exige dois valores");
            }
            case IN -> {
                if (count < 1 || count > 50) throw new IllegalArgumentException("IN exige de 1 a 50 valores");
            }
            default -> {
                if (count != 1) throw new IllegalArgumentException("Operador exige exatamente um valor");
            }
        }
    }

    private static void validateTemporalWindow(
            SemanticDatasetDescriptor descriptor,
            SemanticQuery query,
            Map<String, SemanticDatasetDescriptor.SemanticDimension> dimensions) {
        dimensions.values().stream()
                .filter(dimension -> dimension.tipo() == SemanticDataType.DATE
                        || dimension.tipo() == SemanticDataType.DATETIME)
                .forEach(dimension -> {
                    LocalDate lower = null;
                    LocalDate upper = null;
                    boolean hasFilter = false;
                    for (SemanticQuery.SemanticFilter filter : query.filtros()) {
                        if (!dimension.id().equals(filter.campo())) continue;
                        hasFilter = true;
                        switch (filter.operador()) {
                            case GT, GTE, EQ -> lower = parseDate(filter.valores().getFirst());
                            case LT, LTE -> upper = parseDate(filter.valores().getFirst());
                            case BETWEEN -> {
                                lower = parseDate(filter.valores().get(0));
                                upper = parseDate(filter.valores().get(1));
                            }
                            default -> { }
                        }
                    }
                    if (!hasFilter) return;
                    if (lower == null && upper != null) {
                        throw new IllegalArgumentException("Filtro temporal exige limite inicial: " + dimension.id());
                    }
                    if (lower != null && upper == null) upper = LocalDate.now().plusDays(1);
                    if (lower != null && upper != null) {
                        long days = ChronoUnit.DAYS.between(lower, upper);
                        if (days < 0 || days > descriptor.periodoMaximoDias()) {
                            throw new IllegalArgumentException("Periodo excede o maximo de "
                                    + descriptor.periodoMaximoDias() + " dias para " + descriptor.id());
                        }
                    }
                });
    }

    private static LocalDate parseDate(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (RuntimeException ignored) {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        }
    }
}
