package com.nexo.app.modules.assistente.application.results;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dados tabulares normalizados (colunas tipadas + linhas alinhadas). Os valores sao convertidos para tipos
 * JSON estaveis (numero, texto ISO-8601, booleano) no momento do registro, para que graficos, relatorios e
 * o sandbox Python leiam exatamente o mesmo dado.
 */
public record TabularData(List<Column> columns, List<List<Object>> rows, int totalRows, boolean truncated) {

    public TabularData {
        columns = List.copyOf(columns);
        rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
    }

    public enum ColumnType { TEXTO, INTEIRO, NUMERO, DATA, DATA_HORA, BOOLEANO, UUID }

    public record Column(String name, ColumnType type) {
    }

    public int columnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public List<Map<String, Object>> asMaps(int limit) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (List<Object> row : rows) {
            if (maps.size() >= limit) break;
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) map.put(columns.get(i).name(), i < row.size() ? row.get(i) : null);
            maps.add(map);
        }
        return maps;
    }

    /** Constroi a partir de linhas em mapa (ex.: datasets semanticos), inferindo tipos. */
    public static TabularData fromMaps(List<Map<String, Object>> maps, boolean truncated) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        maps.forEach(map -> names.addAll(map.keySet()));
        List<String> ordered = new ArrayList<>(names);
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            List<Object> row = new ArrayList<>();
            for (String name : ordered) row.add(normalize(map.get(name)));
            rows.add(row);
        }
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) columns.add(new Column(ordered.get(i), inferType(rows, i)));
        return new TabularData(columns, rows, rows.size(), truncated);
    }

    public static ColumnType inferType(List<List<Object>> rows, int index) {
        ColumnType found = null;
        for (List<Object> row : rows) {
            Object value = index < row.size() ? row.get(index) : null;
            if (value == null) continue;
            ColumnType current = switch (value) {
                case Boolean ignored -> ColumnType.BOOLEANO;
                case Integer ignored -> ColumnType.INTEIRO;
                case Long ignored -> ColumnType.INTEIRO;
                case Number ignored -> ColumnType.NUMERO;
                default -> ColumnType.TEXTO;
            };
            if (found == null) found = current;
            else if (found != current) {
                if ((found == ColumnType.INTEIRO && current == ColumnType.NUMERO)
                        || (found == ColumnType.NUMERO && current == ColumnType.INTEIRO)) {
                    found = ColumnType.NUMERO;
                } else {
                    return ColumnType.TEXTO;
                }
            }
        }
        return found == null ? ColumnType.TEXTO : found;
    }

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    /** Converte qualquer valor JDBC/JPA para um tipo JSON estavel. */
    public static Object normalize(Object value) {
        return switch (value) {
            case null -> null;
            case String text -> text;
            case Boolean bool -> bool;
            case Integer integer -> integer;
            case Long longValue -> longValue;
            case Short shortValue -> shortValue.intValue();
            case BigInteger big -> big.bitLength() < 63 ? big.longValue() : new BigDecimal(big);
            case BigDecimal decimal -> {
                BigDecimal stripped = decimal.stripTrailingZeros();
                if (stripped.scale() <= 0 && stripped.precision() - stripped.scale() < 18) yield stripped.longValueExact();
                yield decimal.doubleValue();
            }
            case Double doubleValue -> doubleValue;
            case Float floatValue -> floatValue.doubleValue();
            case Number number -> number.doubleValue();
            case UUID uuid -> uuid.toString();
            case OffsetDateTime odt -> odt.atZoneSameInstant(ZONA).toOffsetDateTime().format(ISO_OFFSET);
            case ZonedDateTime zdt -> zdt.withZoneSameInstant(ZONA).toOffsetDateTime().format(ISO_OFFSET);
            case Instant instant -> instant.atZone(ZONA).toOffsetDateTime().format(ISO_OFFSET);
            case Timestamp timestamp -> timestamp.toInstant().atZone(ZONA).toOffsetDateTime().format(ISO_OFFSET);
            case java.sql.Date date -> date.toLocalDate().toString();
            case LocalDate date -> date.toString();
            case LocalDateTime dateTime -> dateTime.toString();
            case Enum<?> enumValue -> enumValue.name();
            case Map<?, ?> map -> map.toString();
            default -> value.toString();
        };
    }
}
