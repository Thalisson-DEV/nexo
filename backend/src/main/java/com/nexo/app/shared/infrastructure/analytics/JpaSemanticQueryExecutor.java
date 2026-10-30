package com.nexo.app.shared.infrastructure.analytics;

import com.nexo.app.shared.application.analytics.SemanticDatasetDescriptor;
import com.nexo.app.shared.application.analytics.SemanticQuery;
import com.nexo.app.shared.application.analytics.SemanticQueryResult;
import com.nexo.app.shared.application.analytics.SemanticQueryValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compila a DSL semantica para HQL parametrizado usando somente expressoes
 * registradas pelo servidor. Nenhum fragmento fornecido pelo modelo entra no HQL.
 */
public final class JpaSemanticQueryExecutor {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private JpaSemanticQueryExecutor() {
    }

    public static SemanticQueryResult execute(
            EntityManager entityManager,
            SemanticDatasetDescriptor descriptor,
            SemanticQuery semanticQuery,
            String fromClause,
            String basePredicate,
            Map<String, Object> baseParameters,
            Map<String, FieldBinding> fields,
            Map<String, String> measures,
            Consumer<Map<String, Object>> rowEnricher) {
        int limit = SemanticQueryValidator.validateAndResolveLimit(descriptor, semanticQuery);
        boolean aggregate = !semanticQuery.medidas().isEmpty();
        List<String> selected = new ArrayList<>(semanticQuery.dimensoes());
        selected.addAll(semanticQuery.medidas());

        StringBuilder hql = new StringBuilder("select ");
        if (!aggregate) {
            hql.append("distinct ");
        }
        List<String> selections = new ArrayList<>();
        semanticQuery.dimensoes().forEach(id -> selections.add(fields.get(id).expression() + " as " + id));
        semanticQuery.medidas().forEach(id -> selections.add(measures.get(id) + " as " + id));
        hql.append(String.join(", ", selections)).append(' ').append(fromClause);

        Map<String, Object> parameters = new LinkedHashMap<>(baseParameters);
        List<String> predicates = new ArrayList<>();
        if (basePredicate != null && !basePredicate.isBlank()) {
            predicates.add(basePredicate);
        }
        int parameterIndex = 0;
        for (SemanticQuery.SemanticFilter filter : semanticQuery.filtros()) {
            FieldBinding binding = fields.get(filter.campo());
            String parameter = "semanticParam" + parameterIndex++;
            predicates.add(toPredicate(binding.expression(), filter, parameter, parameters, binding.parser()));
        }
        if (!predicates.isEmpty()) {
            hql.append(" where ").append(String.join(" and ", predicates));
        }
        if (aggregate && !semanticQuery.agruparPor().isEmpty()) {
            hql.append(" group by ")
                    .append(String.join(", ", semanticQuery.agruparPor().stream()
                            .map(id -> fields.get(id).expression()).toList()));
        }
        if (!semanticQuery.ordenarPor().isEmpty()) {
            hql.append(" order by ").append(String.join(", ", semanticQuery.ordenarPor().stream()
                    .map(order -> order.campo() + " " + order.direcao().name()).toList()));
        }

        Query query = entityManager.createQuery(hql.toString());
        parameters.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        @SuppressWarnings("unchecked")
        List<Object> rawRows = query.getResultList();
        boolean truncated = rawRows.size() > limit;
        if (truncated) {
            rawRows = rawRows.subList(0, limit);
        }

        List<Map<String, Object>> rows = rawRows.stream()
                .map(row -> mapRow(row, selected, rowEnricher))
                .toList();
        Map<String, Object> effectiveQuery = new LinkedHashMap<>();
        effectiveQuery.put("dimensoes", semanticQuery.dimensoes());
        effectiveQuery.put("medidas", semanticQuery.medidas());
        effectiveQuery.put("filtros", semanticQuery.filtros());
        effectiveQuery.put("agruparPor", semanticQuery.agruparPor());
        effectiveQuery.put("ordenarPor", semanticQuery.ordenarPor());
        effectiveQuery.put("limite", limit);

        return new SemanticQueryResult(
                descriptor.id(),
                descriptor.version(),
                rows,
                rows.size(),
                truncated,
                ZONE_ID.getId(),
                effectiveQuery,
                OffsetDateTime.now(ZONE_ID));
    }

    private static Map<String, Object> mapRow(
            Object raw,
            List<String> selected,
            Consumer<Map<String, Object>> enricher) {
        Object[] values = raw instanceof Object[] array ? array : new Object[]{raw};
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < selected.size(); i++) {
            row.put(selected.get(i), values[i]);
        }
        if (enricher != null) {
            enricher.accept(row);
        }
        return row;
    }

    private static String toPredicate(
            String expression,
            SemanticQuery.SemanticFilter filter,
            String parameter,
            Map<String, Object> parameters,
            Function<String, Object> parser) {
        return switch (filter.operador()) {
            case EQ -> bind(expression + " = :" + parameter, parameter, first(filter, parser), parameters);
            case NE -> bind(expression + " <> :" + parameter, parameter, first(filter, parser), parameters);
            case GT -> bind(expression + " > :" + parameter, parameter, first(filter, parser), parameters);
            case GTE -> bind(expression + " >= :" + parameter, parameter, first(filter, parser), parameters);
            case LT -> bind(expression + " < :" + parameter, parameter, first(filter, parser), parameters);
            case LTE -> bind(expression + " <= :" + parameter, parameter, first(filter, parser), parameters);
            case CONTAINS -> bind(
                    "lower(cast(" + expression + " as string)) like :" + parameter,
                    parameter,
                    "%" + filter.valores().getFirst().toLowerCase() + "%",
                    parameters);
            case IN -> {
                parameters.put(parameter, filter.valores().stream().map(parser).toList());
                yield expression + " in :" + parameter;
            }
            case BETWEEN -> {
                String endParameter = parameter + "End";
                parameters.put(parameter, parser.apply(filter.valores().get(0)));
                parameters.put(endParameter, parser.apply(filter.valores().get(1)));
                yield expression + " between :" + parameter + " and :" + endParameter;
            }
            case IS_NULL -> expression + " is null";
            case IS_NOT_NULL -> expression + " is not null";
        };
    }

    private static Object first(SemanticQuery.SemanticFilter filter, Function<String, Object> parser) {
        return parser.apply(filter.valores().getFirst());
    }

    private static String bind(String predicate, String name, Object value, Map<String, Object> parameters) {
        parameters.put(name, value);
        return predicate;
    }

    public record FieldBinding(String expression, Function<String, Object> parser) {
        public FieldBinding {
            if (expression == null || expression.isBlank() || parser == null) {
                throw new IllegalArgumentException("Field binding invalido");
            }
        }
    }
}
