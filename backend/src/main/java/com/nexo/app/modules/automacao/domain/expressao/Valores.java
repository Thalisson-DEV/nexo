package com.nexo.app.modules.automacao.domain.expressao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Os valores que trafegam entre os passos de uma automação são JSON: mapa, lista,
 * texto, número, verdadeiro/falso ou nulo. Aqui ficam as conversões tolerantes que o
 * motor inteiro usa — "1.234,56" vira número, "não" vira falso, data em texto vira
 * instante —, para que quem monta o fluxo não precise saber de tipos.
 */
public final class Valores {

    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    public static final Locale PT_BR = Locale.of("pt", "BR");
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Valores() {
    }

    public static ObjectMapper json() {
        return JSON;
    }

    /** Número a partir de qualquer valor; {@code null} quando não é número. */
    public static Number numero(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Boolean b) return b ? 1L : 0L;
        if (valor instanceof BigDecimal d) return normalizar(d);
        if (valor instanceof Integer || valor instanceof Long || valor instanceof Short || valor instanceof Byte) {
            return ((Number) valor).longValue();
        }
        if (valor instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return d;
        }
        if (valor instanceof String texto) {
            String bruto = texto.strip().replace("R$", "").replace(" ", "").replace(" ", "");
            if (bruto.isEmpty()) return null;
            if (bruto.contains(",")) bruto = bruto.replace(".", "").replace(",", ".");
            try {
                return normalizar(new BigDecimal(bruto));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Number normalizar(BigDecimal valor) {
        BigDecimal limpo = valor.stripTrailingZeros();
        if (limpo.scale() <= 0 && limpo.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) < 0) {
            return limpo.longValueExact();
        }
        return valor.doubleValue();
    }

    public static double dbl(Object valor) {
        Number n = numero(valor);
        return n == null ? 0 : n.doubleValue();
    }

    /** Número "bonito": inteiro quando não tem casas. */
    public static Number limpo(double valor) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) return null;
        if (valor == Math.rint(valor) && Math.abs(valor) < 9e15) return (long) valor;
        return valor;
    }

    public static boolean verdadeiro(Object valor) {
        if (valor == null) return false;
        if (valor instanceof Boolean b) return b;
        if (valor instanceof String texto) {
            String t = texto.strip().toLowerCase(Locale.ROOT);
            return !(t.isEmpty() || t.equals("false") || t.equals("0") || t.equals("não") || t.equals("nao")
                    || t.equals("no") || t.equals("null"));
        }
        if (valor instanceof Number n) return n.doubleValue() != 0;
        if (valor instanceof Collection<?> c) return !c.isEmpty();
        if (valor instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    public static String texto(Object valor) {
        if (valor == null) return "";
        if (valor instanceof String s) return s;
        if (valor instanceof Boolean b) return b ? "sim" : "não";
        if (valor instanceof Double || valor instanceof Float) {
            double d = ((Number) valor).doubleValue();
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 9e15) return Long.toString((long) d);
            if (!Double.isFinite(d)) return Double.toString(d);
            String t = BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            return t;
        }
        if (valor instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        if (valor instanceof Map<?, ?> || valor instanceof Collection<?>) return paraJson(valor);
        if (valor instanceof OffsetDateTime || valor instanceof ZonedDateTime || valor instanceof LocalDate
                || valor instanceof LocalDateTime) {
            return valor.toString();
        }
        return valor.toString();
    }

    public static String paraJson(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            return String.valueOf(valor);
        }
    }

    /** Cópia "JSON pura" de um valor (records, datas e enums viram texto/mapas). */
    public static Object comoJson(Object valor) {
        if (valor == null || valor instanceof String || valor instanceof Boolean || valor instanceof Long) return valor;
        if (valor instanceof Number n) return numero(n);
        return JSON.convertValue(valor, Object.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapa(Object valor) {
        if (valor instanceof Map<?, ?> m) {
            Map<String, Object> copia = new LinkedHashMap<>();
            m.forEach((k, v) -> copia.put(String.valueOf(k), v));
            return copia;
        }
        return new LinkedHashMap<>();
    }

    public static List<Object> lista(Object valor) {
        if (valor == null) return new ArrayList<>();
        if (valor instanceof List<?> l) return new ArrayList<>(l);
        if (valor instanceof Collection<?> c) return new ArrayList<>(c);
        if (valor instanceof String s && s.isBlank()) return new ArrayList<>();
        List<Object> unica = new ArrayList<>();
        unica.add(valor);
        return unica;
    }

    public static boolean vazio(Object valor) {
        if (valor == null) return true;
        if (valor instanceof String s) return s.isBlank();
        if (valor instanceof Collection<?> c) return c.isEmpty();
        if (valor instanceof Map<?, ?> m) return m.isEmpty();
        return false;
    }

    public static String moeda(Object valor) {
        double numero = dbl(valor);
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return (numero < 0 ? "-" : "") + "R$ " + formato.format(Math.abs(numero));
    }

    public static String numeroFormatado(Object valor, int casas) {
        DecimalFormat formato = new DecimalFormat(casas > 0 ? "#,##0." + "0".repeat(casas) : "#,##0",
                DecimalFormatSymbols.getInstance(PT_BR));
        return formato.format(dbl(valor));
    }

    public static ZonedDateTime instante(Object valor) {
        if (valor == null) return null;
        if (valor instanceof ZonedDateTime z) return z;
        if (valor instanceof OffsetDateTime o) return o.atZoneSameInstant(BRASILIA);
        if (valor instanceof LocalDateTime l) return l.atZone(BRASILIA);
        if (valor instanceof LocalDate d) return d.atStartOfDay(BRASILIA);
        if (!(valor instanceof String texto) || texto.isBlank()) return null;
        String bruto = texto.strip();
        for (String padrao : new String[]{"dd/MM/yyyy HH:mm", "dd/MM/yyyy"}) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(padrao);
                if (padrao.length() > 10) return LocalDateTime.parse(bruto, f).atZone(BRASILIA);
                return LocalDate.parse(bruto, f).atStartOfDay(BRASILIA);
            } catch (DateTimeParseException ignored) {
                // próximo formato
            }
        }
        try {
            return OffsetDateTime.parse(bruto.replace("Z", "+00:00")).atZoneSameInstant(BRASILIA);
        } catch (DateTimeParseException ignored) {
            // tenta sem fuso
        }
        try {
            return ZonedDateTime.parse(bruto);
        } catch (DateTimeParseException ignored) {
            // tenta local
        }
        try {
            return LocalDateTime.parse(bruto).atZone(BRASILIA);
        } catch (DateTimeParseException ignored) {
            // tenta data
        }
        try {
            return LocalDate.parse(bruto).atStartOfDay(BRASILIA);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static String formatarData(Object valor, Object padrao) {
        ZonedDateTime momento = instante(valor);
        if (momento == null) return "";
        ZonedDateTime local = momento.withZoneSameInstant(BRASILIA);
        String formato = switch (texto(padrao)) {
            case "dd/mm/aaaa hh:mm" -> "dd/MM/yyyy HH:mm";
            case "mm/aaaa" -> "MM/yyyy";
            case "aaaa-mm-dd" -> "yyyy-MM-dd";
            case "hh:mm" -> "HH:mm";
            case "dia da semana" -> "EEEE";
            default -> "dd/MM/yyyy";
        };
        return local.format(DateTimeFormatter.ofPattern(formato, PT_BR));
    }

    /** Sem acento, minúsculo e sem espaços nas pontas: "Petrolândia" casa com "PETROLANDIA". */
    public static String comparavel(Object valor) {
        return java.text.Normalizer.normalize(texto(valor).strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
