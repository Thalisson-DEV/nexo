package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Leitura tolerante dos argumentos emitidos pelo modelo (tipos trocados, strings no lugar de listas...). */
public final class ToolArgs {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ToolArgs() {
    }

    public static String text(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        if (node == null || node.isNull()) return null;
        String value = node.isValueNode() ? node.asText() : node.toString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String requiredText(JsonNode args, String field) {
        String value = text(args, field);
        if (value == null) throw new ToolException("Parametro obrigatorio ausente: " + field);
        return value;
    }

    public static List<String> texts(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull()) return values;
        if (node.isArray()) {
            node.forEach(item -> {
                if (item != null && !item.isNull() && !item.asText().isBlank()) values.add(item.asText().trim());
            });
        } else if (!node.asText().isBlank()) {
            for (String part : node.asText().split(",")) if (!part.isBlank()) values.add(part.trim());
        }
        return values;
    }

    public static Integer integer(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asInt();
        try {
            return Integer.valueOf(node.asText().trim());
        } catch (NumberFormatException exception) {
            throw new ToolException("Parametro '" + field + "' deve ser inteiro.");
        }
    }

    public static Double number(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asDouble();
        try {
            return Double.valueOf(node.asText().trim().replace(",", "."));
        } catch (NumberFormatException exception) {
            throw new ToolException("Parametro '" + field + "' deve ser numerico.");
        }
    }

    public static boolean bool(JsonNode args, String field, boolean fallback) {
        JsonNode node = args == null ? null : args.get(field);
        if (node == null || node.isNull()) return fallback;
        if (node.isBoolean()) return node.asBoolean();
        String value = node.asText().trim().toLowerCase();
        return value.equals("true") || value.equals("sim") || value.equals("1");
    }

    public static LocalDate date(JsonNode node, String field) {
        if (node == null || node.isNull() || node.asText().isBlank()) return null;
        return parseDate(node.asText().trim(), field);
    }

    public static LocalDate parseDate(String value, String field) {
        try {
            if (value.matches("\\d{2}/\\d{2}/\\d{4}")) return LocalDate.parse(value, BR);
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (RuntimeException exception) {
            throw new ToolException("Data invalida em '" + field + "': " + value + " (use AAAA-MM-DD).");
        }
    }

    public static JsonNode object(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        return node == null || node.isNull() || !node.isObject() ? null : node;
    }

    public static JsonNode array(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        return node == null || node.isNull() || !node.isArray() ? null : node;
    }
}
