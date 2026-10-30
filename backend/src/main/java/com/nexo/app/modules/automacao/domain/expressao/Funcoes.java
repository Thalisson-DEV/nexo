package com.nexo.app.modules.automacao.domain.expressao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * O catálogo fechado de funções das expressões. Acrescentar uma função é acrescentar
 * uma linha aqui — e o seu texto de ajuda aparece sozinho no seletor de expressões.
 */
public final class Funcoes {

    public record Funcao(int minimo, int maximo, String ajuda, Function<List<Object>, Object> corpo) {
    }

    public static final Map<String, Funcao> TODAS = new LinkedHashMap<>();

    static {
        f("length", 1, 1, "length(lista) — quantidade de itens", a -> tamanho(a.get(0)));
        f("sum", 1, 2, "sum(lista, 'campo') — soma de um campo", a -> soma(a.get(0), arg(a, 1)));
        f("pluck", 2, 2, "pluck(lista, 'campo') — só os valores de um campo", a -> pluck(a.get(0), a.get(1)));
        f("join", 1, 2, "join(lista, ', ') — junta em texto", a -> juntar(a.get(0), a.size() > 1 ? Valores.texto(a.get(1)) : ", "));
        f("split", 1, 2, "split(texto, ',') — separa em lista", a -> separar(a.get(0), a.size() > 1 ? Valores.texto(a.get(1)) : ","));
        f("default", 2, 2, "default(valor, 'padrão') — usa o padrão se vazio",
                a -> a.get(0) == null || "".equals(a.get(0)) ? a.get(1) : a.get(0));
        f("coalesce", 1, 10, "coalesce(a, b, …) — o primeiro não vazio",
                a -> a.stream().filter(v -> v != null && !"".equals(v)).findFirst().orElse(null));
        f("upper", 1, 1, "upper(texto) — maiúsculas", a -> Valores.texto(a.get(0)).toUpperCase(Valores.PT_BR));
        f("lower", 1, 1, "lower(texto) — minúsculas", a -> Valores.texto(a.get(0)).toLowerCase(Valores.PT_BR));
        f("trim", 1, 1, "trim(texto) — sem espaços nas pontas", a -> Valores.texto(a.get(0)).strip());
        f("text", 1, 1, "text(valor) — converte em texto", a -> Valores.texto(a.get(0)));
        f("number", 1, 1, "number(texto) — converte em número", a -> Valores.numero(a.get(0)));
        f("format_money", 1, 1, "format_money(valor) — R$ 1.234,56", a -> Valores.moeda(a.get(0)));
        f("format_number", 1, 2, "format_number(valor, 2) — 1.234,56",
                a -> Valores.numeroFormatado(a.get(0), (int) Valores.dbl(arg(a, 1))));
        f("format_date", 1, 2, "format_date(data, 'dd/mm/aaaa') — formata data",
                a -> Valores.formatarData(a.get(0), a.size() > 1 ? a.get(1) : "dd/mm/aaaa"));
        f("add_days", 2, 2, "add_days(data, 3) — soma dias", a -> somarDias(a.get(0), a.get(1)));
        f("now", 0, 0, "now() — data e hora atual", a -> ZonedDateTime.now(Valores.BRASILIA).toOffsetDateTime().toString());
        f("today", 0, 0, "today() — data de hoje (dd/mm/aaaa)",
                a -> Valores.formatarData(ZonedDateTime.now(Valores.BRASILIA), "dd/mm/aaaa"));
        f("add", 2, 2, "add(a, b)", a -> Valores.limpo(Valores.dbl(a.get(0)) + Valores.dbl(a.get(1))));
        f("sub", 2, 2, "sub(a, b)", a -> Valores.limpo(Valores.dbl(a.get(0)) - Valores.dbl(a.get(1))));
        f("mul", 2, 2, "mul(a, b)", a -> Valores.limpo(Valores.dbl(a.get(0)) * Valores.dbl(a.get(1))));
        f("div", 2, 2, "div(a, b)", a -> {
            double divisor = Valores.dbl(a.get(1));
            return divisor == 0 ? null : Valores.limpo(Valores.dbl(a.get(0)) / divisor);
        });
        f("round", 1, 2, "round(valor, 2)", a -> {
            int casas = (int) Valores.dbl(arg(a, 1));
            BigDecimal valor = BigDecimal.valueOf(Valores.dbl(a.get(0))).setScale(casas, RoundingMode.HALF_UP);
            return casas == 0 ? (Object) valor.longValue() : (Object) valor.doubleValue();
        });
        f("percent", 2, 3, "percent(parte, total, 1) — 12,5%", a -> {
            double total = Valores.dbl(a.get(1));
            if (total == 0) return "0%";
            return Valores.numeroFormatado(Valores.dbl(a.get(0)) * 100 / total, (int) Valores.dbl(arg(a, 2))) + "%";
        });
        f("concat", 1, 20, "concat(a, b, …) — junta textos", a -> {
            StringBuilder sb = new StringBuilder();
            a.forEach(v -> sb.append(Valores.texto(v)));
            return sb.toString();
        });
        f("first", 1, 1, "first(lista) — primeiro item", a -> {
            List<Object> l = a.get(0) instanceof List<?> ? Valores.lista(a.get(0)) : List.of();
            return l.isEmpty() ? null : l.get(0);
        });
        f("last", 1, 1, "last(lista) — último item", a -> {
            List<Object> l = a.get(0) instanceof List<?> ? Valores.lista(a.get(0)) : List.of();
            return l.isEmpty() ? null : l.get(l.size() - 1);
        });
        f("json", 1, 1, "json(texto) — lê JSON", a -> lerJson(a.get(0)));
        f("to_json", 1, 1, "to_json(valor) — escreve JSON", a -> Valores.paraJson(a.get(0)));
        f("contains", 2, 2, "contains(lista_ou_texto, valor)", a -> contem(a.get(0), a.get(1)));
        f("empty", 1, 1, "empty(valor) — vazio?", a -> tamanho(a.get(0)) == 0);
        f("if", 2, 3, "if(condição, se_sim, se_não)", a -> Valores.verdadeiro(a.get(0)) ? a.get(1) : arg(a, 2));
        f("list", 0, 100, "list(a, b, …) — monta uma lista", ArrayList::new);
        f("max", 1, 10, "max(lista) — maior valor", a -> extremo(a, true));
        f("min", 1, 10, "min(lista) — menor valor", a -> extremo(a, false));
        f("days_between", 2, 2, "days_between(data1, data2) — dias entre duas datas", a -> {
            ZonedDateTime x = Valores.instante(a.get(0));
            ZonedDateTime y = Valores.instante(a.get(1));
            if (x == null || y == null) return null;
            return Duration.between(x, y).toDays();
        });
    }

    private Funcoes() {
    }

    private static void f(String nome, int minimo, int maximo, String ajuda, Function<List<Object>, Object> corpo) {
        TODAS.put(nome, new Funcao(minimo, maximo, ajuda, corpo));
    }

    private static Object arg(List<Object> argumentos, int indice) {
        return argumentos.size() > indice ? argumentos.get(indice) : null;
    }

    public static Map<String, String> ajuda() {
        Map<String, String> ajuda = new LinkedHashMap<>();
        TODAS.forEach((nome, funcao) -> ajuda.put(nome, funcao.ajuda()));
        return ajuda;
    }

    static int tamanho(Object valor) {
        if (valor == null) return 0;
        if (valor instanceof String s) return s.length();
        if (valor instanceof Collection<?> c) return c.size();
        if (valor instanceof Map<?, ?> m) return m.size();
        return 1;
    }

    static Object soma(Object valores, Object campo) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object item : Valores.lista(valores)) {
            Object bruto = campo != null && item instanceof Map<?, ?> m ? m.get(Valores.texto(campo)) : item;
            BigDecimal n = Expressoes.decimal(bruto);
            if (n != null) total = total.add(n);
        }
        return Valores.limpo(total.doubleValue());
    }

    static List<Object> pluck(Object valores, Object campo) {
        List<Object> resultado = new ArrayList<>();
        for (Object item : Valores.lista(valores)) {
            resultado.add(item instanceof Map<?, ?> m ? m.get(Valores.texto(campo)) : null);
        }
        return resultado;
    }

    static String juntar(Object valores, String separador) {
        List<String> partes = new ArrayList<>();
        for (Object item : Valores.lista(valores)) partes.add(Valores.texto(item));
        return String.join(separador, partes);
    }

    static List<Object> separar(Object texto, String separador) {
        List<Object> partes = new ArrayList<>();
        for (String parte : Valores.texto(texto).split(java.util.regex.Pattern.quote(separador))) {
            if (!parte.isBlank()) partes.add(parte.strip());
        }
        return partes;
    }

    static boolean contem(Object conteiner, Object valor) {
        if (conteiner == null) return false;
        if (conteiner instanceof String s) return s.toLowerCase(Locale.ROOT).contains(Expressoes.normal(valor));
        if (conteiner instanceof Map<?, ?> m) return m.containsKey(Valores.texto(valor));
        if (conteiner instanceof Collection<?> c) {
            String alvo = Expressoes.normal(valor);
            return c.stream().anyMatch(item -> Expressoes.normal(item).equals(alvo));
        }
        return false;
    }

    private static Object lerJson(Object valor) {
        if (!(valor instanceof String texto)) return valor;
        try {
            return Valores.json().readValue(texto, Object.class);
        } catch (JsonProcessingException e) {
            throw new ErroExpressao("json(): texto não é um JSON válido");
        }
    }

    private static Object somarDias(Object data, Object dias) {
        ZonedDateTime momento = Valores.instante(data);
        Number n = Valores.numero(dias);
        if (momento == null || n == null) return "";
        return momento.plusMinutes(Math.round(n.doubleValue() * 1440)).toOffsetDateTime().toString();
    }

    private static Object extremo(List<Object> argumentos, boolean maior) {
        List<Object> valores = argumentos.size() == 1 && argumentos.get(0) instanceof List<?> ? Valores.lista(argumentos.get(0)) : argumentos;
        if (valores.isEmpty()) return null;
        double atual = Valores.dbl(valores.get(0));
        for (Object v : valores) {
            double d = Valores.dbl(v);
            atual = maior ? Math.max(atual, d) : Math.min(atual, d);
        }
        return Valores.limpo(atual);
    }
}
