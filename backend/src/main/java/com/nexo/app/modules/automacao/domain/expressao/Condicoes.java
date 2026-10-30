package com.nexo.app.modules.automacao.domain.expressao;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Grupos de condição como o editor os monta: regras ligadas por "e"/"ou", com grupos
 * aninhados. Cada lado de uma regra é um modelo ({@code {{ trigger.outputs.base }}} ou
 * texto fixo). Grupo vazio é verdadeiro — sem condição, sempre.
 *
 * <p>Formato JSON: {@code {"op": "and|or", "rules": [{"left", "operator", "right"} | grupo]}}.</p>
 */
public final class Condicoes {

    public static final Map<String, String> OPERADORES = new LinkedHashMap<>();
    public static final Set<String> UNARIOS = Set.of("empty", "not_empty", "is_true", "is_false");
    public static final int MAX_REGRAS = 30;

    static {
        OPERADORES.put("eq", "é igual a");
        OPERADORES.put("ne", "é diferente de");
        OPERADORES.put("gt", "é maior que");
        OPERADORES.put("gte", "é maior ou igual a");
        OPERADORES.put("lt", "é menor que");
        OPERADORES.put("lte", "é menor ou igual a");
        OPERADORES.put("contains", "contém");
        OPERADORES.put("not_contains", "não contém");
        OPERADORES.put("starts_with", "começa com");
        OPERADORES.put("ends_with", "termina com");
        OPERADORES.put("in", "é um de");
        OPERADORES.put("not_in", "não é nenhum de");
        OPERADORES.put("empty", "está vazio");
        OPERADORES.put("not_empty", "não está vazio");
        OPERADORES.put("is_true", "é verdadeiro");
        OPERADORES.put("is_false", "é falso");
    }

    public record Regra(Object esquerda, String operador, Object direita) {
    }

    /** Cada item de {@code regras} é um {@link Regra} ou um {@link Grupo}. */
    public record Grupo(String op, List<Object> regras) {
    }

    private Condicoes() {
    }

    public static Grupo ler(Object bruto) {
        if (bruto instanceof Grupo g) return g;
        if (!(bruto instanceof Map<?, ?> mapa) || mapa.isEmpty()) return new Grupo("and", List.of());
        if (!mapa.containsKey("rules") && mapa.containsKey("left")) {
            return new Grupo("and", List.of(regra(mapa)));
        }
        String op = "or".equals(mapa.get("op")) ? "or" : "and";
        List<Object> regras = new ArrayList<>();
        Object lista = mapa.get("rules");
        if (lista instanceof Collection<?> c) {
            for (Object item : c) {
                if (item instanceof Map<?, ?> m) regras.add(m.containsKey("rules") ? ler(m) : regra(m));
            }
        }
        return new Grupo(op, regras);
    }

    private static Regra regra(Map<?, ?> mapa) {
        Object operador = mapa.get("operator");
        String op = operador == null ? "eq" : String.valueOf(operador);
        if (!OPERADORES.containsKey(op)) throw new ErroExpressao("Operador desconhecido: " + op);
        return new Regra(mapa.get("left"), op, mapa.get("right"));
    }

    public static List<Regra> regras(Grupo grupo) {
        List<Regra> todas = new ArrayList<>();
        for (Object item : grupo.regras()) {
            if (item instanceof Grupo g) todas.addAll(regras(g));
            else todas.add((Regra) item);
        }
        return todas;
    }

    public static boolean avaliar(Grupo grupo, Map<String, Object> escopo) {
        if (grupo.regras().isEmpty()) return true;
        boolean e = !"or".equals(grupo.op());
        for (Object item : grupo.regras()) {
            boolean resultado = item instanceof Grupo g ? avaliar(g, escopo) : avaliarRegra((Regra) item, escopo);
            if (e && !resultado) return false;
            if (!e && resultado) return true;
        }
        return e;
    }

    public static boolean avaliar(Object bruto, Map<String, Object> escopo) {
        return avaliar(ler(bruto), escopo);
    }

    private static boolean avaliarRegra(Regra regra, Map<String, Object> escopo) {
        Object esquerda = Expressoes.renderizar(regra.esquerda(), escopo);
        Object direita = UNARIOS.contains(regra.operador()) ? null : Expressoes.renderizar(regra.direita(), escopo);
        return comparar(regra.operador(), esquerda, direita);
    }

    private static boolean vazio(Object valor) {
        return valor == null || Valores.vazio(valor);
    }

    private static List<Object> opcoes(Object valor) {
        if (valor instanceof List<?>) return Valores.lista(valor);
        if (valor instanceof String s) {
            List<Object> partes = new ArrayList<>();
            for (String parte : s.split("[,;]")) if (!parte.isBlank()) partes.add(parte.strip());
            return partes;
        }
        return valor == null ? List.of() : List.of(valor);
    }

    private static final Set<String> TEXTOS_LOGICOS = Set.of("true", "false", "sim", "não", "nao");

    /**
     * Um lado é booleano e o outro também, ou um texto que o representa. Como texto o
     * booleano vira "sim"/"não", e {@code obraCivil = "false"} nunca bateria.
     */
    private static boolean logicos(Object esquerda, Object direita) {
        if (!(esquerda instanceof Boolean) && !(direita instanceof Boolean)) return false;
        return logico(esquerda) && logico(direita);
    }

    private static boolean logico(Object valor) {
        return valor instanceof Boolean
                || (valor instanceof String s && TEXTOS_LOGICOS.contains(s.strip().toLowerCase(Locale.ROOT)));
    }

    public static boolean comparar(String operador, Object esquerda, Object direita) {
        switch (operador) {
            case "empty":
                return vazio(esquerda);
            case "not_empty":
                return !vazio(esquerda);
            case "is_true":
                return Valores.verdadeiro(esquerda);
            case "is_false":
                return !Valores.verdadeiro(esquerda);
            case "in":
            case "not_in": {
                String alvo = Expressoes.normal(esquerda);
                boolean dentro = opcoes(direita).stream().anyMatch(o -> Expressoes.normal(o).equals(alvo));
                return operador.equals("in") == dentro;
            }
            case "contains":
            case "not_contains": {
                boolean achou;
                if (esquerda instanceof Collection<?> c) {
                    String alvo = Expressoes.normal(direita);
                    achou = c.stream().anyMatch(item -> Expressoes.normal(item).equals(alvo));
                } else {
                    achou = Expressoes.normal(esquerda).contains(Expressoes.normal(direita));
                }
                return operador.equals("contains") == achou;
            }
            case "starts_with":
                return Expressoes.normal(esquerda).startsWith(Expressoes.normal(direita));
            case "ends_with":
                return Expressoes.normal(esquerda).endsWith(Expressoes.normal(direita));
            default:
                break;
        }
        if ((operador.equals("eq") || operador.equals("ne")) && logicos(esquerda, direita)) {
            return operador.equals("eq") == (Valores.verdadeiro(esquerda) == Valores.verdadeiro(direita));
        }
        Number a = Valores.numero(esquerda);
        Number b = Valores.numero(direita);
        if (operador.equals("eq") || operador.equals("ne")) {
            boolean igual = a != null && b != null
                    ? a.doubleValue() == b.doubleValue()
                    : Expressoes.normal(esquerda).equals(Expressoes.normal(direita));
            return operador.equals("eq") == igual;
        }
        double x;
        double y;
        if (a == null || b == null) {
            ZonedDateTime da = Valores.instante(esquerda);
            ZonedDateTime db = Valores.instante(direita);
            if (da == null || db == null) return false;
            x = da.toInstant().toEpochMilli();
            y = db.toInstant().toEpochMilli();
        } else {
            x = a.doubleValue();
            y = b.doubleValue();
        }
        return switch (operador) {
            case "gt" -> x > y;
            case "gte" -> x >= y;
            case "lt" -> x < y;
            default -> x <= y;
        };
    }
}
