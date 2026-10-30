package com.nexo.app.modules.automacao.domain.expressao;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expressões que passam dados entre os passos de uma automação.
 *
 * <p>Um parâmetro é um modelo: texto com trechos {@code {{ expr }}}. Quando o valor
 * inteiro é uma única {@code {{ expr }}} o resultado mantém o tipo (lista, número);
 * misturado com texto, vira texto — como {@code @expr} e {@code @{expr}} do Power
 * Automate.</p>
 *
 * <p>O analisador é próprio, sem {@code eval} nem SpEL: literais, caminhos como
 * {@code steps.buscar.outputs.items[0].valor}, aritmética, comparações,
 * {@code and/or/not} e uma lista fechada de funções. O acesso a caminhos tolera nulos:
 * uma chave ausente dá {@code null}. É a mesma fronteira das regras de notificação —
 * quem configura tem poder sobre os dados, nunca sobre o código do servidor.</p>
 */
public final class Expressoes {

    public static final Set<String> RAIZES = Set.of("trigger", "steps", "vars", "item", "loop", "run", "automation", "env");
    public static final int MAX_CARACTERES = 2000;
    private static final Pattern MODELO = Pattern.compile("\\{\\{(.*?)}}", Pattern.DOTALL);
    private static final Pattern INTEIRA = Pattern.compile("^\\{\\{((?:(?!}}).)*)}}$", Pattern.DOTALL);
    private static final Map<String, No> CACHE = new ConcurrentHashMap<>();

    private Expressoes() {
    }

    // ------------------------------------------------------------------------------ AST

    public sealed interface No permits Literal, Caminho, Chamada, Unario, Binario {
    }

    public record Literal(Object valor) implements No {
    }

    /** Partes: String (chave) ou No (índice calculado). */
    public record Caminho(String raiz, List<Object> partes) implements No {
    }

    public record Chamada(String nome, List<No> argumentos) implements No {
    }

    public record Unario(String operador, No operando) implements No {
    }

    public record Binario(String operador, No esquerda, No direita) implements No {
    }

    /** Uma referência lida por um modelo (para o verificador). */
    public record Referencia(String raiz, String nome, List<String> resto) {
    }

    // ------------------------------------------------------------------------------ tokens

    private record Token(String tipo, Object valor) {
        boolean op(String simbolo) {
            return tipo.equals("op") && valor.equals(simbolo);
        }
    }

    private static final Pattern TOKEN = Pattern.compile(
            "(?<espaco>\\s+)|(?<num>\\d+(?:\\.\\d+)?)|(?<str>'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")"
                    + "|(?<nome>[A-Za-z_][A-Za-z0-9_]*)|(?<op>==|!=|>=|<=|[-+*/%().,\\[\\]<>!])");

    private static List<Token> tokens(String texto) {
        if (texto.length() > MAX_CARACTERES) throw new ErroExpressao("Expressão longa demais");
        List<Token> resultado = new ArrayList<>();
        Matcher m = TOKEN.matcher(texto);
        int pos = 0;
        while (pos < texto.length()) {
            if (!m.find(pos) || m.start() != pos) {
                throw new ErroExpressao("Caractere inesperado '" + texto.charAt(pos) + "'");
            }
            if (m.group("num") != null) {
                String bruto = m.group("num");
                resultado.add(new Token("num", bruto.contains(".") ? (Object) Double.parseDouble(bruto) : (Object) Long.parseLong(bruto)));
            } else if (m.group("str") != null) {
                String corpo = m.group("str");
                corpo = corpo.substring(1, corpo.length() - 1).replaceAll("\\\\(.)", "$1");
                resultado.add(new Token("str", corpo));
            } else if (m.group("nome") != null) {
                resultado.add(new Token("nome", m.group("nome")));
            } else if (m.group("op") != null) {
                resultado.add(new Token("op", m.group("op")));
            }
            pos = m.end();
        }
        resultado.add(new Token("fim", null));
        return resultado;
    }

    // ------------------------------------------------------------------------------ parser

    private static final Map<String, Integer> PRECEDENCIA = Map.ofEntries(
            Map.entry("or", 1), Map.entry("and", 2),
            Map.entry("==", 4), Map.entry("!=", 4), Map.entry(">", 4), Map.entry(">=", 4), Map.entry("<", 4), Map.entry("<=", 4),
            Map.entry("+", 5), Map.entry("-", 5), Map.entry("*", 6), Map.entry("/", 6), Map.entry("%", 6));

    private static final class Parser {
        private final List<Token> tokens;
        private int indice;

        Parser(String texto) {
            this.tokens = tokens(texto);
        }

        Token espiar() {
            return tokens.get(indice);
        }

        Token tomar() {
            return tokens.get(indice++);
        }

        void esperar(String simbolo) {
            Token t = tomar();
            if (!t.op(simbolo)) throw new ErroExpressao("Esperava '" + simbolo + "'");
        }

        No analisar() {
            if (espiar().tipo().equals("fim")) throw new ErroExpressao("Expressão vazia");
            No no = expressao(0);
            if (!espiar().tipo().equals("fim")) throw new ErroExpressao("Sobrou '" + espiar().valor() + "' na expressão");
            return no;
        }

        String operadorBinario() {
            Token t = espiar();
            if (t.tipo().equals("op") && PRECEDENCIA.containsKey((String) t.valor())) return (String) t.valor();
            if (t.tipo().equals("nome") && ("and".equals(t.valor()) || "or".equals(t.valor()))) return (String) t.valor();
            return null;
        }

        No expressao(int minimo) {
            No esquerda = unario();
            while (true) {
                String op = operadorBinario();
                if (op == null || PRECEDENCIA.get(op) < minimo) return esquerda;
                int precedencia = PRECEDENCIA.get(op);
                tomar();
                No direita = expressao(precedencia + 1);
                esquerda = new Binario(op, esquerda, direita);
            }
        }

        No unario() {
            Token t = espiar();
            if (t.op("-") || t.op("!") || (t.tipo().equals("nome") && "not".equals(t.valor()))) {
                tomar();
                No operando = expressao(t.op("-") ? 7 : 3);
                return new Unario(t.op("-") ? "-" : "not", operando);
            }
            return posfixo(primario());
        }

        No primario() {
            Token t = tomar();
            if (t.tipo().equals("num") || t.tipo().equals("str")) return new Literal(t.valor());
            if (t.op("(")) {
                No no = expressao(0);
                esperar(")");
                return no;
            }
            if (t.op("[")) {
                List<No> itens = new ArrayList<>();
                if (!espiar().op("]")) {
                    itens.add(expressao(0));
                    while (espiar().op(",")) {
                        tomar();
                        itens.add(expressao(0));
                    }
                }
                esperar("]");
                return new Chamada("list", itens);
            }
            if (t.tipo().equals("nome")) {
                String nome = (String) t.valor();
                if (nome.equals("true") || nome.equals("false")) return new Literal(nome.equals("true"));
                if (nome.equals("null") || nome.equals("none")) return new Literal(null);
                if (espiar().op("(")) {
                    tomar();
                    List<No> argumentos = new ArrayList<>();
                    if (!espiar().op(")")) {
                        argumentos.add(expressao(0));
                        while (espiar().op(",")) {
                            tomar();
                            argumentos.add(expressao(0));
                        }
                    }
                    esperar(")");
                    Funcoes.Funcao funcao = Funcoes.TODAS.get(nome);
                    if (funcao == null) throw new ErroExpressao("Função desconhecida: " + nome + "()");
                    if (argumentos.size() < funcao.minimo() || argumentos.size() > funcao.maximo()) {
                        throw new ErroExpressao(nome + "() recebeu " + argumentos.size() + " argumento(s)");
                    }
                    return new Chamada(nome, argumentos);
                }
                if (!RAIZES.contains(nome)) {
                    throw new ErroExpressao("'" + nome + "' não existe. Use trigger, steps, vars, item, loop, run, automation ou env");
                }
                return new Caminho(nome, List.of());
            }
            throw new ErroExpressao("Não esperava '" + t.valor() + "'");
        }

        No posfixo(No no) {
            while (true) {
                Token t = espiar();
                if (t.op(".")) {
                    tomar();
                    Token chave = tomar();
                    if (!chave.tipo().equals("nome") && !chave.tipo().equals("num")) {
                        throw new ErroExpressao("Esperava um nome depois do ponto");
                    }
                    no = estender(no, String.valueOf(chave.valor()));
                } else if (t.op("[")) {
                    tomar();
                    No indiceNo = expressao(0);
                    esperar("]");
                    no = estender(no, indiceNo instanceof Literal l ? (l.valor() == null ? "" : l.valor()) : indiceNo);
                } else {
                    return no;
                }
            }
        }

        static No estender(No no, Object parte) {
            if (no instanceof Caminho c) {
                List<Object> partes = new ArrayList<>(c.partes());
                partes.add(parte);
                return new Caminho(c.raiz(), List.copyOf(partes));
            }
            No chave = parte instanceof No n ? n : new Literal(parte);
            return new Chamada("__get", List.of(no, chave));
        }
    }

    public static No analisar(String texto) {
        String chave = texto.strip();
        No cache = CACHE.get(chave);
        if (cache != null) return cache;
        No no = new Parser(chave).analisar();
        if (CACHE.size() < 5000) CACHE.put(chave, no);
        return no;
    }

    // ------------------------------------------------------------------------------ avaliação

    public static Object obter(Object conteiner, Object chave) {
        if (conteiner == null) return null;
        if (conteiner instanceof Map<?, ?> mapa) {
            return mapa.get(chave instanceof String s ? s : Valores.texto(chave));
        }
        if (conteiner instanceof List<?> lista) {
            Number n = Valores.numero(chave);
            if (!(n instanceof Long indice)) return null;
            int i = indice.intValue();
            if (i < 0) i += lista.size();
            return i >= 0 && i < lista.size() ? lista.get(i) : null;
        }
        return null;
    }

    public static boolean comparar(String op, Object esquerda, Object direita) {
        Number a = Valores.numero(esquerda);
        Number b = Valores.numero(direita);
        boolean numerico = a != null && b != null;
        if (op.equals("==") || op.equals("!=")) {
            boolean igual;
            if (numerico) {
                igual = a.doubleValue() == b.doubleValue();
            } else if (vazioOuNulo(esquerda) || vazioOuNulo(direita)) {
                igual = vazioOuNulo(esquerda) && vazioOuNulo(direita);
            } else {
                igual = Valores.texto(esquerda).strip().equalsIgnoreCase(Valores.texto(direita).strip());
            }
            return op.equals("==") == igual;
        }
        double x;
        double y;
        if (numerico) {
            x = a.doubleValue();
            y = b.doubleValue();
        } else {
            ZonedDateTime da = Valores.instante(esquerda);
            ZonedDateTime db = Valores.instante(direita);
            if (da == null || db == null) return false;
            x = da.toInstant().toEpochMilli();
            y = db.toInstant().toEpochMilli();
        }
        return switch (op) {
            case "<" -> x < y;
            case "<=" -> x <= y;
            case ">" -> x > y;
            default -> x >= y;
        };
    }

    private static boolean vazioOuNulo(Object valor) {
        return valor == null || (valor instanceof String s && s.isEmpty());
    }

    public static Object avaliar(No no, Map<String, Object> escopo) {
        if (no instanceof Literal l) return l.valor();
        if (no instanceof Caminho c) {
            Object valor = escopo.get(c.raiz());
            for (Object parte : c.partes()) {
                Object chave = parte instanceof No n ? avaliar(n, escopo) : parte;
                valor = obter(valor, chave);
            }
            return valor;
        }
        if (no instanceof Chamada ch) {
            if (ch.nome().equals("__get")) {
                return obter(avaliar(ch.argumentos().get(0), escopo), avaliar(ch.argumentos().get(1), escopo));
            }
            List<Object> argumentos = new ArrayList<>();
            for (No argumento : ch.argumentos()) argumentos.add(avaliar(argumento, escopo));
            try {
                return Funcoes.TODAS.get(ch.nome()).corpo().apply(argumentos);
            } catch (ErroExpressao e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ErroExpressao(ch.nome() + "(): " + e.getMessage());
            }
        }
        if (no instanceof Unario u) {
            Object valor = avaliar(u.operando(), escopo);
            if (u.operador().equals("-")) return Valores.limpo(-Valores.dbl(valor));
            return !Valores.verdadeiro(valor);
        }
        Binario b = (Binario) no;
        if (b.operador().equals("and")) {
            return Valores.verdadeiro(avaliar(b.esquerda(), escopo)) && Valores.verdadeiro(avaliar(b.direita(), escopo));
        }
        if (b.operador().equals("or")) {
            return Valores.verdadeiro(avaliar(b.esquerda(), escopo)) || Valores.verdadeiro(avaliar(b.direita(), escopo));
        }
        Object esquerda = avaliar(b.esquerda(), escopo);
        Object direita = avaliar(b.direita(), escopo);
        switch (b.operador()) {
            case "==", "!=", "<", "<=", ">", ">=" -> {
                return comparar(b.operador(), esquerda, direita);
            }
            default -> {
                // segue
            }
        }
        if (b.operador().equals("+") && (esquerda instanceof String || direita instanceof String)
                && (Valores.numero(esquerda) == null || Valores.numero(direita) == null)) {
            return Valores.texto(esquerda) + Valores.texto(direita);
        }
        double x = Valores.dbl(esquerda);
        double y = Valores.dbl(direita);
        return switch (b.operador()) {
            case "+" -> Valores.limpo(x + y);
            case "-" -> Valores.limpo(x - y);
            case "*" -> Valores.limpo(x * y);
            case "/" -> y == 0 ? null : Valores.limpo(x / y);
            default -> y == 0 ? null : Valores.limpo(x % y);
        };
    }

    public static Object avaliar(String texto, Map<String, Object> escopo) {
        return avaliar(analisar(texto), escopo);
    }

    // ------------------------------------------------------------------------------ modelos

    public static boolean ehModelo(Object valor) {
        return valor instanceof String s && s.contains("{{");
    }

    /** Resolve um modelo; listas e mapas são resolvidos recursivamente. */
    public static Object renderizar(Object valor, Map<String, Object> escopo) {
        if (valor instanceof List<?> lista) {
            List<Object> resultado = new ArrayList<>();
            for (Object item : lista) resultado.add(renderizar(item, escopo));
            return resultado;
        }
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            mapa.forEach((k, v) -> resultado.put(String.valueOf(k), renderizar(v, escopo)));
            return resultado;
        }
        if (!(valor instanceof String texto) || !texto.contains("{{")) return valor;
        Matcher inteira = INTEIRA.matcher(texto.strip());
        if (inteira.matches()) return avaliar(inteira.group(1), escopo);
        return substituir(texto, expr -> Valores.texto(avaliar(expr, escopo)));
    }

    public static String renderizarTexto(Object valor, Map<String, Object> escopo) {
        return Valores.texto(renderizar(valor, escopo));
    }

    public static String substituir(String texto, Function<String, String> resolver) {
        Matcher m = MODELO.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(resolver.apply(m.group(1))));
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------------------ análise estática

    public static List<Referencia> referencias(Object valor) {
        List<Referencia> encontradas = new ArrayList<>();
        if (valor instanceof Collection<?> lista) {
            for (Object item : lista) encontradas.addAll(referencias(item));
            return encontradas;
        }
        if (valor instanceof Map<?, ?> mapa) {
            for (Object item : mapa.values()) encontradas.addAll(referencias(item));
            return encontradas;
        }
        if (!(valor instanceof String texto) || !texto.contains("{{")) return encontradas;
        if (contar(texto, "{{") != contar(texto, "}}")) throw new ErroExpressao("Chaves {{ }} sem fechamento");
        Matcher m = MODELO.matcher(texto);
        while (m.find()) coletar(analisar(m.group(1)), encontradas);
        return encontradas;
    }

    private static int contar(String texto, String trecho) {
        int total = 0;
        int i = texto.indexOf(trecho);
        while (i >= 0) {
            total++;
            i = texto.indexOf(trecho, i + trecho.length());
        }
        return total;
    }

    private static void coletar(No no, List<Referencia> encontradas) {
        if (no instanceof Caminho c) {
            List<Object> partes = c.partes();
            String nome = !partes.isEmpty() && partes.get(0) instanceof String s ? s : null;
            List<String> resto = new ArrayList<>();
            for (int i = 1; i < partes.size(); i++) if (partes.get(i) instanceof String s) resto.add(s);
            encontradas.add(new Referencia(c.raiz(), nome, resto));
            for (Object parte : partes) if (parte instanceof No n) coletar(n, encontradas);
        } else if (no instanceof Chamada ch) {
            for (No argumento : ch.argumentos()) coletar(argumento, encontradas);
        } else if (no instanceof Unario u) {
            coletar(u.operando(), encontradas);
        } else if (no instanceof Binario b) {
            coletar(b.esquerda(), encontradas);
            coletar(b.direita(), encontradas);
        }
    }

    /** Usado por funções de comparação textual tolerante. */
    static String normal(Object valor) {
        return Valores.texto(valor).strip().toLowerCase(Locale.ROOT);
    }

    static BigDecimal decimal(Object valor) {
        Number n = Valores.numero(valor);
        return n == null ? null : new BigDecimal(n.toString());
    }
}
