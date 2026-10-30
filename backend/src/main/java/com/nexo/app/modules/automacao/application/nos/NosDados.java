package com.nexo.app.modules.automacao.application.nos;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecCampo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.domain.expressao.Condicoes;
import com.nexo.app.modules.automacao.domain.expressao.ErroExpressao;
import com.nexo.app.modules.automacao.domain.expressao.Expressoes;
import com.nexo.app.modules.automacao.domain.expressao.Valores;

/**
 * Passos de dados: trazer dados para o fluxo (texto, JSON, tabela colada) e remodelar o
 * que os passos anteriores produziram. Funções puras sobre valores JSON.
 */
@Component
public class NosDados implements ProvedorNos {

    public static final int MAX_LINHAS = 5000;
    public static final int MAX_TEXTO = 200_000;
    private static final String CABECALHO = "padding:7px 9px;border-bottom:2px solid #cbd5e1;font-size:12px;text-align:left;color:#64748b";
    private static final String CELULA = "padding:7px 9px;border-bottom:1px solid #e2e8f0;font-size:13px;vertical-align:top;color:#334155";

    static List<Object> itens(Object valor, String rotulo) {
        if (valor == null || "".equals(valor)) return new ArrayList<>();
        if (valor instanceof List<?>) return Valores.lista(valor);
        if (valor instanceof Map<?, ?>) return new ArrayList<>(List.of(valor));
        if (valor instanceof String s && s.strip().startsWith("[")) {
            try {
                Object lido = Valores.json().readValue(s, Object.class);
                if (lido instanceof List<?>) return Valores.lista(lido);
            } catch (JsonProcessingException e) {
                throw new ErroAcao(rotulo + ": o texto não é uma lista JSON");
            }
        }
        throw new ErroAcao(rotulo + ": esperava uma lista (escolha a saída de lista de um passo anterior)");
    }

    static Object campo(Object item, String chave) {
        if (item instanceof Map<?, ?> m) {
            if (m.containsKey(chave)) return m.get(chave);
            Object atual = item;
            for (String parte : chave.split("\\.")) atual = atual instanceof Map<?, ?> mm ? mm.get(parte) : null;
            return atual;
        }
        return chave.isEmpty() ? item : null;
    }

    @Override
    public Collection<EspecNo> nos() {
        List<EspecNo> nos = new ArrayList<>();
        nos.add(EspecNo.de("data.input", "data", "Inserir dados",
                        "Traz dados para o fluxo: texto livre, JSON ou tabela colada (CSV/planilha).", "database")
                .params(EspecParam.de("source", "Tipo de dado", "select").padrao("text").estatico()
                                .opcoes(List.of(List.of("text", "Texto livre"), List.of("table", "Tabela (colada do Excel ou CSV)"), List.of("json", "JSON"))).criar(),
                        EspecParam.de("content", "Conteúdo", "textarea").placeholder("Cole aqui, ou use conteúdo dinâmico").criar(),
                        EspecParam.de("has_header", "A primeira linha é o cabeçalho", "boolean").padrao(true).estatico()
                                .mostrarSe("source", "table").criar())
                .saidas(EspecSaida.de("text", "Texto", "string"), EspecSaida.de("rows", "Linhas (tabela)", "array"),
                        EspecSaida.de("columns", "Colunas", "array"), EspecSaida.de("count", "Quantidade de linhas", "number"),
                        EspecSaida.de("data", "Dados (como vieram)", "any"))
                .executor(NosDados::inserir)
                .palavras("colar", "texto", "planilha", "csv", "json", "tabela")
                .criar());
        nos.add(EspecNo.de("data.compose", "data", "Compor", "Monta um valor (texto, número, lista ou objeto) a partir de outros dados.", "compose")
                .params(EspecParam.de("value", "Valor", "textarea").obrigatorio().placeholder("Escreva o texto e inclua dados dos passos anteriores").criar())
                .saidas(EspecSaida.de("value", "Valor", "any"))
                .executor((ctx, p) -> mapa("value", p.get("value")))
                .palavras("compose", "montar", "texto", "fórmula", "expressão")
                .criar());
        nos.add(EspecNo.de("data.parse_json", "data", "Ler JSON", "Transforma um texto JSON em dados que os próximos passos podem usar.", "braces")
                .params(EspecParam.de("content", "Conteúdo", "textarea").obrigatorio().criar())
                .saidas(EspecSaida.de("value", "Dados", "any"))
                .executor(NosDados::lerJson)
                .palavras("parse", "json", "converter")
                .criar());
        nos.add(EspecNo.de("data.filter", "data", "Filtrar lista", "Mantém só os itens que atendem às regras. Nas regras, use o item atual.", "filter")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().semResolver().placeholder("Escolha a lista de um passo anterior").criar(),
                        EspecParam.de("condition", "Manter quando", "condition").obrigatorio().padrao(Map.of("op", "and", "rules", List.of())).semResolver().criar(),
                        EspecParam.de("keep_rest", "Guardar também os que não atendem", "boolean").padrao(false).estatico().criar())
                .saidas(EspecSaida.de("items", "Itens que atendem", "array"), EspecSaida.de("count", "Quantidade", "number"),
                        EspecSaida.de("rest", "Itens que não atendem", "array"), EspecSaida.de("rest_count", "Quantidade que não atende", "number"))
                .executor(NosDados::filtrar)
                .palavras("filter", "where", "onde", "separar")
                .criar());
        nos.add(EspecNo.de("data.select", "data", "Selecionar campos", "Cria uma lista nova escolhendo e renomeando os campos de cada item.", "columns")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().semResolver().criar(),
                        EspecParam.de("mapping", "Campos", "mapping").obrigatorio().padrao(List.of()).semResolver()
                                .campos(EspecCampo.texto("key", "Campo novo", null), EspecCampo.expressao("value", "Valor", "Escolha um dado de um passo anterior")).criar())
                .saidas(EspecSaida.de("items", "Lista", "array"), EspecSaida.de("count", "Quantidade", "number"))
                .executor(NosDados::selecionar)
                .palavras("map", "select", "renomear", "campos")
                .criar());
        nos.add(EspecNo.de("data.sort", "data", "Ordenar lista", "Ordena por um campo e, se quiser, fica só com os primeiros.", "sort")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().criar(),
                        EspecParam.de("by", "Campo", "text").placeholder("valor").criar(),
                        EspecParam.de("order", "Ordem", "select").padrao("desc").estatico()
                                .opcoes(List.of(List.of("asc", "Crescente"), List.of("desc", "Decrescente"))).criar(),
                        EspecParam.de("limit", "Manter só os primeiros (0 = todos)", "number").padrao(0).faixa(0, 5000).criar())
                .saidas(EspecSaida.de("items", "Lista", "array"), EspecSaida.de("count", "Quantidade", "number"))
                .executor(NosDados::ordenar)
                .palavras("sort", "ordenar", "top", "ranking")
                .criar());
        nos.add(EspecNo.de("data.group", "data", "Agrupar lista", "Agrupa os itens por um campo (ex.: base), com quantidade e soma.", "layers")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().criar(),
                        EspecParam.de("by", "Agrupar por", "text").obrigatorio().placeholder("base").criar(),
                        EspecParam.de("sum_field", "Somar o campo", "text").placeholder("valor").criar())
                .saidas(EspecSaida.lista("groups", "Grupos", List.of(EspecSaida.de("key", "Valor do grupo", "string"),
                                EspecSaida.de("count", "Quantidade", "number"), EspecSaida.de("total", "Soma", "number"),
                                EspecSaida.de("total_formatado", "Soma (R$)", "string"), EspecSaida.de("items", "Itens do grupo", "array"))),
                        EspecSaida.de("count", "Quantidade de grupos", "number"))
                .executor(NosDados::agrupar)
                .palavras("group by", "agrupar", "por base")
                .criar());
        nos.add(EspecNo.de("data.aggregate", "data", "Calcular total", "Soma, média, mínimo, máximo ou contagem de um campo da lista.", "sigma")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().criar(),
                        EspecParam.de("operation", "Cálculo", "select").padrao("sum").estatico()
                                .opcoes(List.of(List.of("sum", "Soma"), List.of("avg", "Média"), List.of("min", "Mínimo"),
                                        List.of("max", "Máximo"), List.of("count", "Contagem"))).criar(),
                        EspecParam.de("field", "Campo", "text").placeholder("valor").criar())
                .saidas(EspecSaida.de("value", "Resultado", "number"), EspecSaida.de("formatted", "Resultado (R$)", "string"))
                .executor(NosDados::agregar)
                .palavras("soma", "total", "média", "sum", "count")
                .criar());
        nos.add(EspecNo.de("data.table", "data", "Criar tabela", "Monta uma tabela (HTML para e-mail, texto e CSV) a partir de uma lista.", "table")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().semResolver().criar(),
                        EspecParam.de("columns", "Colunas (vazio = automáticas)", "columns").padrao(List.of()).semResolver()
                                .campos(EspecCampo.texto("header", "Título", null), EspecCampo.expressao("value", "Valor", "Escolha um dado de um passo anterior")).criar())
                .saidas(EspecSaida.de("html", "Tabela HTML", "string"), EspecSaida.de("text", "Tabela em texto", "string"),
                        EspecSaida.de("csv", "CSV", "string"), EspecSaida.de("count", "Linhas", "number"))
                .executor(NosDados::tabela)
                .palavras("html", "tabela", "csv", "relatório")
                .criar());
        nos.add(EspecNo.de("data.join", "data", "Juntar em texto", "Junta os itens de uma lista (ou um campo deles) num texto só.", "link")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().criar(),
                        EspecParam.de("field", "Campo (opcional)", "text").placeholder("nome").criar(),
                        EspecParam.de("separator", "Separador", "text").padrao(", ").placeholder(", ou \\n").criar())
                .saidas(EspecSaida.de("text", "Texto", "string"))
                .executor((ctx, p) -> {
                    String campo = Valores.texto(p.get("field")).strip();
                    String sep = Valores.vazio(p.get("separator")) ? ", " : Valores.texto(p.get("separator")).replace("\\n", "\n");
                    List<String> partes = new ArrayList<>();
                    for (Object item : itens(p.get("items"), "Lista")) {
                        String v = Valores.texto(campo.isEmpty() ? item : campo(item, campo));
                        if (!v.isEmpty()) partes.add(v);
                    }
                    return mapa("text", String.join(sep, partes));
                })
                .palavras("join", "concatenar", "lista")
                .criar());
        nos.add(EspecNo.de("data.markdown", "data", "Formatar texto (Markdown → HTML)",
                        "Converte um texto em Markdown (como as análises do Copiloto) em HTML pronto para o e-mail.", "text")
                .params(EspecParam.de("content", "Texto em Markdown", "textarea").obrigatorio().criar())
                .saidas(EspecSaida.de("html", "HTML", "string"))
                .executor((ctx, p) -> mapa("html", MarkdownHtml.converter(Valores.texto(p.get("content")))))
                .palavras("markdown", "html", "formatar")
                .criar());
        return nos;
    }

    static Map<String, Object> mapa(String chave, Object valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(chave, valor);
        return m;
    }

    private static Map<String, Object> inserir(ContextoAcao ctx, Map<String, Object> p) {
        String fonte = Valores.texto(p.getOrDefault("source", "text"));
        Object conteudo = p.get("content");
        Map<String, Object> r = new LinkedHashMap<>();
        if (fonte.equals("json")) {
            Object lido = conteudo instanceof Map<?, ?> || conteudo instanceof List<?> ? conteudo : lerJsonTexto(Valores.texto(conteudo));
            List<Object> linhas = lido instanceof List<?> ? Valores.lista(lido) : List.of();
            LinkedHashSet<String> colunas = new LinkedHashSet<>();
            for (Object l : linhas) if (l instanceof Map<?, ?> m) m.keySet().forEach(k -> colunas.add(String.valueOf(k)));
            r.put("data", lido);
            r.put("rows", linhas);
            r.put("columns", new ArrayList<>(colunas));
            r.put("count", linhas.size());
            r.put("text", Valores.paraJson(lido));
            return r;
        }
        String texto = Valores.texto(conteudo);
        if (texto.length() > MAX_TEXTO) throw new ErroAcao("Texto longo demais (máximo 200 mil caracteres)");
        if (fonte.equals("table")) {
            boolean cabecalho = !Boolean.FALSE.equals(p.get("has_header"));
            char sep = texto.chars().filter(c -> c == ';').count() > texto.chars().filter(c -> c == ',').count() ? ';'
                    : texto.contains("\t") ? '\t' : ',';
            List<List<String>> linhas = new ArrayList<>();
            try {
                for (CSVRecord rec : CSVFormat.DEFAULT.builder().setDelimiter(sep).build().parse(new StringReader(texto))) {
                    List<String> l = new ArrayList<>();
                    rec.forEach(l::add);
                    if (l.stream().anyMatch(c -> !c.isBlank())) linhas.add(l);
                }
            } catch (IOException | RuntimeException e) {
                throw new ErroAcao("Não consegui ler a tabela: " + e.getMessage());
            }
            if (linhas.size() > MAX_LINHAS + 1) throw new ErroAcao("No máximo " + MAX_LINHAS + " linhas");
            int largura = linhas.stream().mapToInt(List::size).max().orElse(0);
            List<String> colunas = new ArrayList<>();
            for (int i = 0; i < largura; i++) {
                String nome = cabecalho && !linhas.isEmpty() && i < linhas.get(0).size() && !linhas.get(0).get(i).isBlank()
                        ? linhas.get(0).get(i).strip() : "coluna_" + (i + 1);
                colunas.add(nome);
            }
            List<Object> registros = new ArrayList<>();
            for (int j = cabecalho ? 1 : 0; j < linhas.size(); j++) {
                Map<String, Object> reg = new LinkedHashMap<>();
                for (int i = 0; i < largura; i++) reg.put(colunas.get(i), i < linhas.get(j).size() ? linhas.get(j).get(i) : null);
                registros.add(reg);
            }
            r.put("data", registros);
            r.put("rows", registros);
            r.put("columns", colunas);
            r.put("count", registros.size());
            r.put("text", texto);
            return r;
        }
        r.put("data", texto);
        r.put("rows", List.of());
        r.put("columns", List.of());
        r.put("count", 0);
        r.put("text", texto);
        return r;
    }

    private static Object lerJsonTexto(String texto) {
        try {
            return Valores.json().readValue(texto.isBlank() ? "null" : texto, Object.class);
        } catch (JsonProcessingException e) {
            throw new ErroAcao("JSON inválido: linha " + e.getLocation().getLineNr() + ", coluna " + e.getLocation().getColumnNr());
        }
    }

    private static Map<String, Object> lerJson(ContextoAcao ctx, Map<String, Object> p) {
        Object conteudo = p.get("content");
        if (conteudo instanceof Map<?, ?> || conteudo instanceof List<?>) return mapa("value", conteudo);
        String texto = Valores.texto(conteudo).strip();
        int inicio = -1;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '{' || texto.charAt(i) == '[') {
                inicio = i;
                break;
            }
        }
        if (inicio < 0) throw new ErroAcao("Não há JSON no conteúdo");
        int fim = Math.max(texto.lastIndexOf('}'), texto.lastIndexOf(']'));
        return mapa("value", lerJsonTexto(texto.substring(inicio, fim + 1)));
    }

    private static Map<String, Object> filtrar(ContextoAcao ctx, Map<String, Object> p) {
        List<Object> lista = itens(ctx.resolver(p.get("items")), "Lista");
        Condicoes.Grupo grupo = Condicoes.ler(p.get("condition"));
        List<Object> mantidos = new ArrayList<>();
        List<Object> resto = new ArrayList<>();
        try {
            for (Object item : lista) {
                Map<String, Object> escopo = new java.util.HashMap<>(ctx.escopo());
                escopo.put("item", item);
                if (Condicoes.avaliar(grupo, escopo)) mantidos.add(item);
                else resto.add(item);
            }
        } catch (ErroExpressao e) {
            throw new ErroAcao("Regras: " + e.getMessage());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", mantidos);
        r.put("count", mantidos.size());
        r.put("rest", Valores.verdadeiro(p.get("keep_rest")) ? resto : List.of());
        r.put("rest_count", resto.size());
        return r;
    }

    private static Map<String, Object> selecionar(ContextoAcao ctx, Map<String, Object> p) {
        List<Object> lista = itens(ctx.resolver(p.get("items")), "Lista");
        List<Map<String, Object>> mapeamento = new ArrayList<>();
        for (Object e : Valores.lista(p.get("mapping"))) {
            Map<String, Object> m = Valores.mapa(e);
            if (!Valores.texto(m.get("key")).isBlank()) mapeamento.add(m);
        }
        if (mapeamento.isEmpty()) throw new ErroAcao("Informe ao menos um campo");
        List<Object> resultado = new ArrayList<>();
        for (Object item : lista) {
            Map<String, Object> novo = new LinkedHashMap<>();
            for (Map<String, Object> m : mapeamento) {
                novo.put(Valores.texto(m.get("key")).strip(), ctx.resolver(m.get("value"), Map.of("item", item)));
            }
            resultado.add(novo);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", resultado);
        r.put("count", resultado.size());
        return r;
    }

    private static Map<String, Object> ordenar(ContextoAcao ctx, Map<String, Object> p) {
        List<Object> lista = itens(p.get("items"), "Lista");
        String chave = Valores.texto(p.get("by")).strip();
        boolean desc = "desc".equals(Valores.texto(p.getOrDefault("order", "desc")));
        lista.sort((a, b) -> {
            Object va = chave.isEmpty() ? a : campo(a, chave);
            Object vb = chave.isEmpty() ? b : campo(b, chave);
            Number na = Valores.numero(va);
            Number nb = Valores.numero(vb);
            int c;
            if (na != null && nb != null) c = Double.compare(na.doubleValue(), nb.doubleValue());
            else if (na != null) c = -1;
            else if (nb != null) c = 1;
            else c = Valores.texto(va).compareToIgnoreCase(Valores.texto(vb));
            return desc ? -c : c;
        });
        int limite = (int) Valores.dbl(p.get("limit"));
        List<Object> resultado = limite > 0 && limite < lista.size() ? new ArrayList<>(lista.subList(0, limite)) : lista;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", resultado);
        r.put("count", resultado.size());
        return r;
    }

    private static Map<String, Object> agrupar(ContextoAcao ctx, Map<String, Object> p) {
        List<Object> lista = itens(p.get("items"), "Lista");
        String chave = Valores.texto(p.get("by")).strip();
        String soma = Valores.texto(p.get("sum_field")).strip();
        if (chave.isEmpty()) throw new ErroAcao("Informe o campo de agrupamento");
        Map<String, List<Object>> grupos = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Object item : lista) {
            String g = Valores.texto(campo(item, chave));
            grupos.computeIfAbsent(g.isEmpty() ? "(vazio)" : g, k -> new ArrayList<>()).add(item);
        }
        List<Object> resultado = new ArrayList<>();
        grupos.forEach((nome, itensGrupo) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("key", nome);
            g.put("count", itensGrupo.size());
            g.put("items", itensGrupo);
            if (!soma.isEmpty()) {
                BigDecimal total = BigDecimal.ZERO;
                for (Object i : itensGrupo) {
                    Number n = Valores.numero(campo(i, soma));
                    if (n != null) total = total.add(new BigDecimal(n.toString()));
                }
                g.put("total", Valores.limpo(total.doubleValue()));
                g.put("total_formatado", Valores.moeda(total));
            }
            resultado.add(g);
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("groups", resultado);
        r.put("count", resultado.size());
        return r;
    }

    private static Map<String, Object> agregar(ContextoAcao ctx, Map<String, Object> p) {
        List<Object> lista = itens(p.get("items"), "Lista");
        String campo = Valores.texto(p.get("field")).strip();
        String op = Valores.texto(p.getOrDefault("operation", "sum"));
        List<Double> numeros = new ArrayList<>();
        for (Object item : lista) {
            Number n = Valores.numero(campo.isEmpty() ? item : campo(item, campo));
            if (n != null) numeros.add(n.doubleValue());
        }
        Object valor;
        if (op.equals("count")) valor = lista.size();
        else if (numeros.isEmpty()) valor = 0;
        else valor = switch (op) {
            case "avg" -> Valores.limpo(numeros.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            case "min" -> Valores.limpo(numeros.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            case "max" -> Valores.limpo(numeros.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            default -> Valores.limpo(numeros.stream().mapToDouble(Double::doubleValue).sum());
        };
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("value", valor);
        r.put("formatted", op.equals("count") ? Valores.texto(valor) : Valores.moeda(valor));
        return r;
    }

    private static Map<String, Object> tabela(ContextoAcao ctx, Map<String, Object> p) {
        List<Object> lista = itens(ctx.resolver(p.get("items")), "Lista");
        List<Map<String, Object>> colunas = new ArrayList<>();
        for (Object c : Valores.lista(p.get("columns"))) {
            Map<String, Object> m = Valores.mapa(c);
            if (!Valores.vazio(m.get("header")) || !Valores.vazio(m.get("value"))) colunas.add(m);
        }
        if (colunas.isEmpty()) {
            LinkedHashSet<String> chaves = new LinkedHashSet<>();
            for (Object item : lista.subList(0, Math.min(50, lista.size()))) {
                if (item instanceof Map<?, ?> m) m.keySet().forEach(k -> chaves.add(String.valueOf(k)));
            }
            for (String k : chaves.stream().limit(12).toList()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("header", NosGatilhos.rotulo(k));
                m.put("value", "{{ item." + k + " }}");
                colunas.add(m);
            }
        }
        StringBuilder html = new StringBuilder("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%;margin:4px 0 16px\"><thead><tr>");
        List<String> titulos = new ArrayList<>();
        for (Map<String, Object> c : colunas) {
            String t = Valores.texto(c.get("header"));
            titulos.add(t);
            html.append("<th style=\"").append(CABECALHO).append("\">").append(MarkdownHtml.escapar(t)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        StringBuilder texto = new StringBuilder(String.join(" | ", titulos)).append('\n');
        StringWriter csv = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(csv, CSVFormat.DEFAULT.builder().setDelimiter(';').build())) {
            printer.printRecord(titulos);
            for (Object item : lista) {
                List<String> valores = new ArrayList<>();
                html.append("<tr>");
                for (Map<String, Object> c : colunas) {
                    String v = Valores.texto(Expressoes.renderizar(c.get("value"), comItem(ctx, item)));
                    valores.add(v);
                    html.append("<td style=\"").append(CELULA).append("\">").append(MarkdownHtml.escapar(v)).append("</td>");
                }
                html.append("</tr>");
                texto.append(String.join(" | ", valores)).append('\n');
                printer.printRecord(valores);
            }
        } catch (IOException e) {
            throw new ErroAcao("Falha ao montar o CSV");
        }
        html.append("</tbody></table>");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("html", html.toString());
        r.put("text", texto.toString());
        r.put("csv", csv.toString());
        r.put("count", lista.size());
        return r;
    }

    private static Map<String, Object> comItem(ContextoAcao ctx, Object item) {
        Map<String, Object> escopo = new java.util.HashMap<>(ctx.escopo());
        escopo.put("item", item);
        return escopo;
    }
}
