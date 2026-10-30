package com.nexo.app.modules.assistente.application.artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.agent.ToolArgs;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.results.TabularExport;
import com.nexo.app.modules.assistente.application.results.TabularOps;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Resolve componentes declarativos (grafico, tabela, KPI, texto) contra os resultados rN da conversa.
 * O modelo descreve O QUE mostrar (resultado, colunas, agregacao); os numeros sao sempre calculados aqui
 * a partir dos dados registrados, nunca copiados do texto do modelo. A mesma especificacao resolvida
 * alimenta o painel de artefatos (Chart.js no frontend) e o relatorio PDF.
 */
@Component
public class ArtifactResolver {

    public static final List<String> TIPOS_COMPONENTE = List.of("kpi", "grafico", "tabela", "texto");
    public static final List<String> TIPOS_GRAFICO = List.of("barra", "barra_horizontal", "barra_empilhada", "linha",
            "area", "pizza", "rosca", "dispersao");
    public static final List<String> AGREGACOES = List.of("soma", "media", "contagem", "minimo", "maximo", "mediana", "nenhuma");
    public static final List<String> FORMATOS = List.of("numero", "inteiro", "moeda", "percentual");
    public static final List<String> GRANULARIDADES = List.of("dia", "semana", "mes", "ano");
    public static final List<String> ORDENACOES = List.of("valor_desc", "valor_asc", "rotulo", "nenhuma");

    private static final int MAX_COMPONENTES = 24;
    private static final int MAX_SERIES = 8;
    private static final int MAX_PONTOS = 400;
    private static final int MAX_PONTOS_DISPERSAO = 2_000;
    private static final int MAX_LINHAS_TABELA = 500;
    private static final int MAX_TEXTO = 20_000;

    private final ResultStore store;

    public ArtifactResolver(ResultStore store) {
        this.store = store;
    }

    /** Resolve uma lista de componentes; {@code procedencia} acumula os resultados usados. */
    public List<Map<String, Object>> componentes(UUID conversaId, JsonNode componentes, Procedencia procedencia) {
        if (componentes == null || !componentes.isArray() || componentes.isEmpty()) {
            throw new ToolException("Informe 'componentes' com ao menos um item (kpi, grafico, tabela ou texto).");
        }
        if (componentes.size() > MAX_COMPONENTES) {
            throw new ToolException("No maximo " + MAX_COMPONENTES + " componentes por artefato.");
        }
        List<Map<String, Object>> resolvidos = new ArrayList<>();
        int indice = 0;
        for (JsonNode componente : componentes) {
            indice++;
            try {
                resolvidos.add(componente(conversaId, componente, procedencia));
            } catch (ToolException exception) {
                throw new ToolException("Componente " + indice + ": " + exception.getMessage());
            }
        }
        return resolvidos;
    }

    public Map<String, Object> componente(UUID conversaId, JsonNode spec, Procedencia procedencia) {
        if (spec == null || !spec.isObject()) throw new ToolException("Componente deve ser um objeto.");
        String tipo = Objects.toString(ToolArgs.text(spec, "tipo"), "").toLowerCase(Locale.ROOT);
        Map<String, Object> resolvido = switch (tipo) {
            case "kpi", "indicador" -> kpi(conversaId, spec, procedencia);
            case "grafico", "chart" -> grafico(conversaId, spec, procedencia);
            case "tabela", "table" -> tabela(conversaId, spec, procedencia);
            case "texto", "markdown" -> texto(spec);
            default -> throw new ToolException("Tipo de componente invalido: '" + tipo + "'. Use kpi, grafico, tabela ou texto.");
        };
        String largura = ToolArgs.text(spec, "largura");
        if (largura != null) resolvido.put("largura", largura.equalsIgnoreCase("metade") ? "metade" : "inteira");
        return resolvido;
    }

    // ------------------------------------------------------------------------------------ KPI

    Map<String, Object> kpi(UUID conversaId, JsonNode spec, Procedencia procedencia) {
        ResultStore.StoredResult resultado = resultado(conversaId, spec, procedencia);
        String agregacao = agregacao(spec, "soma");
        double valor = agregarColuna(resultado.data(), ToolArgs.text(spec, "coluna"), agregacao, ToolArgs.object(spec, "filtro"));
        Map<String, Object> kpi = base("kpi", spec, resultado);
        kpi.put("valor", TabularOps.arredondar(valor));
        kpi.put("formato", formato(spec, resultado.data(), ToolArgs.text(spec, "coluna")));
        kpi.put("agregacao", agregacao);
        String melhor = ToolArgs.text(spec, "melhor_quando");
        if (melhor != null) kpi.put("melhorQuando", melhor.toLowerCase(Locale.ROOT).startsWith("men") ? "menor" : "maior");
        JsonNode comparacao = ToolArgs.object(spec, "comparar_com");
        if (comparacao != null) {
            ResultStore.StoredResult base = ToolArgs.text(comparacao, "resultado") == null ? resultado
                    : resultado(conversaId, comparacao, procedencia);
            String colunaBase = Optional.ofNullable(ToolArgs.text(comparacao, "coluna")).orElse(ToolArgs.text(spec, "coluna"));
            double valorBase = agregarColuna(base.data(), colunaBase, agregacao(comparacao, agregacao),
                    ToolArgs.object(comparacao, "filtro"));
            Map<String, Object> resumo = new LinkedHashMap<>();
            resumo.put("valor", TabularOps.arredondar(valorBase));
            resumo.put("variacao", valorBase == 0 ? null : TabularOps.arredondar((valor - valorBase) / Math.abs(valorBase)));
            resumo.put("diferenca", TabularOps.arredondar(valor - valorBase));
            resumo.put("rotulo", Optional.ofNullable(ToolArgs.text(comparacao, "rotulo")).orElse("vs. referencia"));
            kpi.put("comparacao", resumo);
        }
        return kpi;
    }

    private static double agregarColuna(TabularData data, String coluna, String agregacao, JsonNode filtro) {
        TabularData filtrado = data;
        if (filtro != null && ToolArgs.text(filtro, "coluna") != null) {
            filtrado = TabularOps.filtrar(data, ToolArgs.text(filtro, "coluna"), Objects.toString(ToolArgs.text(filtro, "valor"), ""));
        }
        if (coluna == null || "contagem".equals(agregacao)) {
            if (coluna == null && !"contagem".equals(agregacao)) {
                throw new ToolException("Informe 'coluna' para a agregacao '" + agregacao + "' ou use agregacao 'contagem'.");
            }
            if (coluna == null) return filtrado.rows().size();
        }
        int indice = TabularOps.exigirColuna(filtrado, coluna);
        List<Double> valores = new ArrayList<>();
        for (List<Object> row : filtrado.rows()) {
            Double numero = TabularOps.numero(indice < row.size() ? row.get(indice) : null);
            if ("contagem".equals(agregacao)) {
                if (indice < row.size() && row.get(indice) != null) valores.add(1.0);
            } else if (numero != null) {
                valores.add(numero);
            }
        }
        if ("contagem".equals(agregacao)) return valores.size();
        return TabularOps.agregar(valores, "nenhuma".equals(agregacao) ? "soma" : agregacao);
    }

    // ------------------------------------------------------------------------------------ grafico

    Map<String, Object> grafico(UUID conversaId, JsonNode spec, Procedencia procedencia) {
        ResultStore.StoredResult resultado = resultado(conversaId, spec, procedencia);
        TabularData data = resultado.data();
        String tipo = Objects.toString(ToolArgs.text(spec, "grafico"), "barra").toLowerCase(Locale.ROOT);
        if (!TIPOS_GRAFICO.contains(tipo)) {
            throw new ToolException("Tipo de grafico invalido: '" + tipo + "'. Use " + String.join(", ", TIPOS_GRAFICO) + ".");
        }
        String colunaX = ToolArgs.requiredText(spec, "x");
        int indiceX = TabularOps.exigirColuna(data, colunaX);
        List<String> colunasY = ToolArgs.texts(spec, "y");
        String agregacao = agregacao(spec, colunasY.isEmpty() ? "contagem" : "soma");
        Map<String, Object> grafico = base("grafico", spec, resultado);
        grafico.put("grafico", tipo);
        grafico.put("eixoX", Optional.ofNullable(ToolArgs.text(spec, "titulo_eixo_x")).orElse(TabularExport.rotulo(data.columns().get(indiceX).name())));

        if (tipo.equals("dispersao")) {
            if (colunasY.size() != 1) throw new ToolException("Grafico de dispersao exige exatamente uma coluna em 'y'.");
            int indiceY = TabularOps.exigirColuna(data, colunasY.getFirst());
            String colunaRotulo = ToolArgs.text(spec, "serie");
            int indiceRotulo = colunaRotulo == null ? -1 : TabularOps.exigirColuna(data, colunaRotulo);
            List<Map<String, Object>> pontos = new ArrayList<>();
            for (List<Object> row : data.rows()) {
                Double x = TabularOps.numero(row.get(indiceX));
                Double y = TabularOps.numero(row.get(indiceY));
                if (x == null || y == null) continue;
                Map<String, Object> ponto = new LinkedHashMap<>();
                ponto.put("x", TabularOps.arredondar(x));
                ponto.put("y", TabularOps.arredondar(y));
                if (indiceRotulo >= 0) ponto.put("rotulo", Objects.toString(row.get(indiceRotulo), ""));
                pontos.add(ponto);
                if (pontos.size() >= MAX_PONTOS_DISPERSAO) break;
            }
            if (pontos.isEmpty()) throw new ToolException("As colunas x e y precisam ser numericas para o grafico de dispersao.");
            grafico.put("pontos", pontos);
            grafico.put("eixoY", Optional.ofNullable(ToolArgs.text(spec, "titulo_eixo_y")).orElse(TabularExport.rotulo(colunasY.getFirst())));
            grafico.put("formato", formato(spec, data, colunasY.getFirst()));
            return grafico;
        }

        List<Integer> indicesY = colunasY.stream().map(coluna -> TabularOps.exigirColuna(data, coluna)).toList();
        String colunaSerie = ToolArgs.text(spec, "serie");
        int indiceSerie = colunaSerie == null ? -1 : TabularOps.exigirColuna(data, colunaSerie);
        if (indiceSerie >= 0 && indicesY.size() > 1) {
            throw new ToolException("Use 'serie' com uma unica coluna em 'y' (ou varias colunas em 'y' sem 'serie').");
        }
        TabularData.ColumnType tipoX = data.columns().get(indiceX).type();
        String granularidade = ToolArgs.text(spec, "granularidade");
        boolean temporal = granularidade != null || tipoX == TabularData.ColumnType.DATA || tipoX == TabularData.ColumnType.DATA_HORA;
        if (temporal && granularidade == null) granularidade = periodosDistintos(data, indiceX) > 62 ? "mes" : "dia";
        if (granularidade != null && !GRANULARIDADES.contains(granularidade.toLowerCase(Locale.ROOT))) {
            throw new ToolException("Granularidade invalida. Use dia, semana, mes ou ano.");
        }

        // chave X -> (nome da serie -> valores)
        LinkedHashMap<String, LinkedHashMap<String, List<Double>>> grupos = new LinkedHashMap<>();
        LinkedHashSet<String> nomesSeries = new LinkedHashSet<>();
        for (List<Object> row : data.rows()) {
            Object bruto = row.get(indiceX);
            String chave = temporal ? TabularOps.periodo(bruto, granularidade.toLowerCase(Locale.ROOT)) : rotuloCategoria(bruto);
            if (chave == null) continue;
            LinkedHashMap<String, List<Double>> porSerie = grupos.computeIfAbsent(chave, ignored -> new LinkedHashMap<>());
            if (indicesY.isEmpty()) {
                String serie = indiceSerie >= 0 ? rotuloCategoria(row.get(indiceSerie)) : "Quantidade";
                nomesSeries.add(serie);
                porSerie.computeIfAbsent(serie, ignored -> new ArrayList<>()).add(1.0);
            } else if (indiceSerie >= 0) {
                String serie = rotuloCategoria(row.get(indiceSerie));
                nomesSeries.add(serie);
                Double valor = TabularOps.numero(row.get(indicesY.getFirst()));
                List<Double> valores = porSerie.computeIfAbsent(serie, ignored -> new ArrayList<>());
                if (valor != null) valores.add("contagem".equals(agregacao) ? 1.0 : valor);
            } else {
                for (int i = 0; i < indicesY.size(); i++) {
                    String serie = TabularExport.rotulo(colunasY.get(i));
                    nomesSeries.add(serie);
                    Double valor = TabularOps.numero(row.get(indicesY.get(i)));
                    List<Double> valores = porSerie.computeIfAbsent(serie, ignored -> new ArrayList<>());
                    if (valor != null) valores.add("contagem".equals(agregacao) ? 1.0 : valor);
                }
            }
        }
        if (grupos.isEmpty()) throw new ToolException("O resultado " + resultado.codigo() + " nao tem valores para o eixo x informado.");
        String funcao = indicesY.isEmpty() || "contagem".equals(agregacao) ? "soma" : ("nenhuma".equals(agregacao) ? "soma" : agregacao);

        List<String> series = new ArrayList<>(nomesSeries);
        Map<String, Double> totalPorSerie = new HashMap<>();
        LinkedHashMap<String, Map<String, Double>> valores = new LinkedHashMap<>();
        grupos.forEach((chave, porSerie) -> {
            Map<String, Double> agregados = new HashMap<>();
            porSerie.forEach((serie, lista) -> {
                double valor = lista.isEmpty() ? 0 : TabularOps.agregar(lista, funcao);
                agregados.put(serie, valor);
                totalPorSerie.merge(serie, Math.abs(valor), Double::sum);
            });
            valores.put(chave, agregados);
        });
        String observacao = null;
        if (series.size() > MAX_SERIES) {
            series.sort(Comparator.comparingDouble((String serie) -> totalPorSerie.getOrDefault(serie, 0.0)).reversed());
            List<String> principais = new ArrayList<>(series.subList(0, MAX_SERIES - 1));
            Set<String> agrupadas = new HashSet<>(series.subList(MAX_SERIES - 1, series.size()));
            boolean somavel = "soma".equals(funcao);
            for (Map<String, Double> agregados : valores.values()) {
                double outros = 0;
                for (String serie : agrupadas) outros += agregados.getOrDefault(serie, 0.0);
                if (somavel) agregados.put("Outros", outros);
            }
            if (somavel) principais.add("Outros");
            observacao = "Series agrupadas: " + (series.size() - MAX_SERIES + 1) + " menores em \"Outros\".";
            series = principais;
        }

        List<String> chaves = new ArrayList<>(valores.keySet());
        String ordenar = Optional.ofNullable(ToolArgs.text(spec, "ordenar")).map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(temporal || tipo.equals("linha") || tipo.equals("area") ? "rotulo" : "valor_desc");
        switch (ordenar) {
            case "valor_desc" -> chaves.sort(Comparator.comparingDouble((String chave) -> total(valores.get(chave))).reversed());
            case "valor_asc" -> chaves.sort(Comparator.comparingDouble((String chave) -> total(valores.get(chave))));
            case "rotulo" -> {
                Comparator<String> ordem = temporal ? Comparator.<String>naturalOrder() : Comparator.comparing(TabularOps::normalizar);
                chaves.sort(ordem);
            }
            default -> {
                // mantem a ordem do resultado
            }
        }
        int limitePadrao = temporal || tipo.equals("linha") || tipo.equals("area") ? MAX_PONTOS
                : (tipo.equals("pizza") || tipo.equals("rosca") ? 8 : 20);
        int limite = Math.max(2, Math.min(MAX_PONTOS, Optional.ofNullable(ToolArgs.integer(spec, "limite")).orElse(limitePadrao)));
        if (chaves.size() > limite) {
            int total = chaves.size();
            boolean circular = tipo.equals("pizza") || tipo.equals("rosca");
            if (circular && "soma".equals(funcao)) {
                List<String> principais = new ArrayList<>(chaves.subList(0, limite - 1));
                Map<String, Double> outros = new HashMap<>();
                for (String chave : chaves.subList(limite - 1, chaves.size())) {
                    valores.get(chave).forEach((serie, valor) -> outros.merge(serie, valor, Double::sum));
                }
                valores.put("Outros", outros);
                principais.add("Outros");
                chaves = principais;
                observacao = juntar(observacao, (total - limite + 1) + " categorias menores somadas em \"Outros\".");
            } else {
                chaves = new ArrayList<>(chaves.subList(0, limite));
                observacao = juntar(observacao, "Exibindo " + limite + " de " + total + " categorias.");
            }
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        for (String serie : series) {
            List<Object> pontos = new ArrayList<>();
            for (String chave : chaves) {
                Double valor = valores.get(chave).get(serie);
                pontos.add(valor == null ? null : TabularOps.arredondar(valor));
            }
            datasets.add(Map.of("nome", serie, "valores", pontos));
        }
        grafico.put("rotulos", chaves.stream().map(chave -> temporal ? rotuloPeriodo(chave) : chave).toList());
        grafico.put("series", datasets);
        grafico.put("empilhado", tipo.equals("barra_empilhada"));
        grafico.put("eixoY", Optional.ofNullable(ToolArgs.text(spec, "titulo_eixo_y"))
                .orElse(indicesY.isEmpty() ? "Quantidade" : indicesY.size() == 1 ? TabularExport.rotulo(colunasY.getFirst())
                        + ("soma".equals(agregacao) || "nenhuma".equals(agregacao) ? "" : " (" + agregacao + ")") : "Valor"));
        grafico.put("formato", formato(spec, data, colunasY.isEmpty() ? null : colunasY.getFirst()));
        grafico.put("agregacao", indicesY.isEmpty() ? "contagem" : agregacao);
        if (observacao != null) grafico.put("observacao", observacao);
        return grafico;
    }

    private static double total(Map<String, Double> valores) {
        return valores == null ? 0 : valores.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static long periodosDistintos(TabularData data, int indice) {
        return data.rows().stream().map(row -> TabularOps.periodo(row.get(indice), "dia")).filter(Objects::nonNull).distinct().count();
    }

    /** "2026-09" -> "09/2026"; "2026-09-25" -> "25/09/2026"; "2026-S38" -> "Sem. 38/2026". */
    static String rotuloPeriodo(String chave) {
        if (chave.matches("\\d{4}-\\d{2}-\\d{2}")) return chave.substring(8, 10) + "/" + chave.substring(5, 7) + "/" + chave.substring(0, 4);
        if (chave.matches("\\d{4}-\\d{2}")) return chave.substring(5, 7) + "/" + chave.substring(0, 4);
        if (chave.matches("\\d{4}-S\\d{2}")) return "Sem. " + chave.substring(6) + "/" + chave.substring(0, 4);
        return chave;
    }

    private static String rotuloCategoria(Object valor) {
        if (valor == null) return "(vazio)";
        String texto = valor.toString().strip();
        return texto.isEmpty() ? "(vazio)" : (texto.length() > 80 ? texto.substring(0, 77) + "..." : texto);
    }

    // ------------------------------------------------------------------------------------ tabela

    Map<String, Object> tabela(UUID conversaId, JsonNode spec, Procedencia procedencia) {
        ResultStore.StoredResult resultado = resultado(conversaId, spec, procedencia);
        TabularData data = resultado.data();
        List<String> pedidas = ToolArgs.texts(spec, "colunas");
        List<Integer> indices = pedidas.isEmpty()
                ? java.util.stream.IntStream.range(0, Math.min(data.columns().size(), 12)).boxed().toList()
                : pedidas.stream().map(coluna -> TabularOps.exigirColuna(data, coluna)).toList();
        List<List<Object>> linhas = new ArrayList<>(data.rows());
        String ordenarPor = ToolArgs.text(spec, "ordenar_por");
        if (ordenarPor != null) {
            int indice = TabularOps.exigirColuna(data, ordenarPor);
            boolean decrescente = ToolArgs.bool(spec, "decrescente", true);
            Comparator<List<Object>> comparador = (a, b) -> comparar(a.get(indice), b.get(indice));
            linhas.sort(decrescente ? comparador.reversed() : comparador);
        }
        int limite = Math.max(1, Math.min(MAX_LINHAS_TABELA, Optional.ofNullable(ToolArgs.integer(spec, "limite")).orElse(100)));
        List<List<Object>> exibidas = new ArrayList<>();
        for (List<Object> linha : linhas) {
            if (exibidas.size() >= limite) break;
            List<Object> selecionada = new ArrayList<>();
            for (int indice : indices) selecionada.add(indice < linha.size() ? linha.get(indice) : null);
            exibidas.add(selecionada);
        }
        Map<String, Object> tabela = base("tabela", spec, resultado);
        List<Map<String, Object>> colunas = new ArrayList<>();
        for (int indice : indices) {
            TabularData.Column coluna = data.columns().get(indice);
            colunas.add(Map.of("nome", coluna.name(), "rotulo", TabularExport.rotulo(coluna.name()),
                    "tipo", coluna.type().name().toLowerCase(Locale.ROOT)));
        }
        tabela.put("colunas", colunas);
        tabela.put("linhas", exibidas);
        tabela.put("total", data.totalRows());
        tabela.put("exibidas", exibidas.size());
        if (pedidas.isEmpty() && data.columns().size() > indices.size()) {
            tabela.put("observacao", "Exibindo as " + indices.size() + " primeiras de " + data.columns().size() + " colunas.");
        }
        return tabela;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int comparar(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        Double na = TabularOps.numero(a);
        Double nb = TabularOps.numero(b);
        if (na != null && nb != null) return Double.compare(na, nb);
        if (a instanceof Comparable ca && a.getClass().equals(b.getClass())) return ca.compareTo(b);
        return TabularOps.normalizar(a.toString()).compareTo(TabularOps.normalizar(b.toString()));
    }

    // ------------------------------------------------------------------------------------ texto

    Map<String, Object> texto(JsonNode spec) {
        String conteudo = ToolArgs.text(spec, "conteudo");
        if (conteudo == null) throw new ToolException("Componente de texto exige 'conteudo' (markdown).");
        Map<String, Object> texto = new LinkedHashMap<>();
        texto.put("tipo", "texto");
        String titulo = ToolArgs.text(spec, "titulo");
        if (titulo != null) texto.put("titulo", titulo);
        texto.put("conteudo", conteudo.length() > MAX_TEXTO ? conteudo.substring(0, MAX_TEXTO) : conteudo);
        return texto;
    }

    // ------------------------------------------------------------------------------------ comum

    private ResultStore.StoredResult resultado(UUID conversaId, JsonNode spec, Procedencia procedencia) {
        String codigo = ToolArgs.text(spec, "resultado");
        if (codigo == null) throw new ToolException("Informe 'resultado' (codigo rN retornado por uma ferramenta de dados).");
        ResultStore.StoredResult resultado = store.exigir(conversaId, codigo);
        procedencia.registrar(resultado);
        return resultado;
    }

    private static Map<String, Object> base(String tipo, JsonNode spec, ResultStore.StoredResult resultado) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("tipo", tipo);
        String titulo = ToolArgs.text(spec, "titulo");
        base.put("titulo", titulo == null ? resultado.titulo() : titulo);
        String descricao = ToolArgs.text(spec, "descricao");
        if (descricao != null) base.put("descricao", descricao);
        base.put("resultado", resultado.codigo());
        base.put("procedencia", resultado.natureza().name());
        return base;
    }

    private static String agregacao(JsonNode spec, String padrao) {
        String valor = Objects.toString(ToolArgs.text(spec, "agregacao"), padrao).toLowerCase(Locale.ROOT);
        valor = switch (valor) {
            case "sum", "total" -> "soma";
            case "avg", "average", "mean" -> "media";
            case "count", "quantidade" -> "contagem";
            case "min" -> "minimo";
            case "max" -> "maximo";
            case "median" -> "mediana";
            default -> valor;
        };
        if (!AGREGACOES.contains(valor)) throw new ToolException("Agregacao invalida: " + valor + ". Use " + String.join(", ", AGREGACOES) + ".");
        return valor;
    }

    private static String formato(JsonNode spec, TabularData data, String coluna) {
        String informado = ToolArgs.text(spec, "formato");
        if (informado != null && FORMATOS.contains(informado.toLowerCase(Locale.ROOT))) return informado.toLowerCase(Locale.ROOT);
        if (coluna == null) return "inteiro";
        String nome = TabularOps.normalizar(coluna);
        if (nome.contains("valor") || nome.contains("custo") || nome.contains("preco") || nome.contains("montante")
                || nome.contains("total_r") || nome.contains("desconto")) {
            return "moeda";
        }
        if (nome.contains("percent") || nome.contains("taxa") || nome.contains("pct")) return "percentual";
        int indice = data.columnIndex(coluna);
        return indice >= 0 && data.columns().get(indice).type() == TabularData.ColumnType.INTEIRO ? "inteiro" : "numero";
    }

    private static String juntar(String a, String b) {
        return a == null ? b : a + " " + b;
    }

    /** Acumula os resultados usados por um artefato (procedencia exibida ao usuario e no PDF). */
    public static final class Procedencia {
        private final Map<String, ResultStore.StoredResult> usados = new LinkedHashMap<>();

        public void registrar(ResultStore.StoredResult resultado) {
            usados.putIfAbsent(resultado.codigo(), resultado);
        }

        public Collection<ResultStore.StoredResult> resultados() {
            return usados.values();
        }

        public List<String> codigos() {
            return List.copyOf(usados.keySet());
        }

        public List<Map<String, Object>> resumo() {
            List<Map<String, Object>> resumo = new ArrayList<>();
            for (ResultStore.StoredResult resultado : usados.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("codigo", resultado.codigo());
                item.put("titulo", resultado.titulo());
                item.put("natureza", resultado.natureza().name());
                item.put("origem", resultado.origem());
                item.put("linhas", resultado.data().totalRows());
                if (resultado.consulta() != null && !resultado.consulta().isEmpty()) item.put("consulta", resultado.consulta());
                resumo.add(item);
            }
            return resumo;
        }
    }
}
