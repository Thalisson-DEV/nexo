package com.nexo.app.modules.assistente.application.results;

import com.nexo.app.modules.assistente.application.agent.ToolException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * Operacoes analiticas deterministicas sobre resultados tabulares (agrupar, estatisticas, series,
 * conciliacao entre fontes e deteccao de anomalias). Garante analise confiavel mesmo sem o sandbox Python.
 */
public final class TabularOps {

    private TabularOps() {
    }

    public static TabularData agrupar(TabularData data, List<String> dimensoes, String metrica, String agregacao) {
        List<Integer> indicesDimensao = dimensoes.stream().map(nome -> exigirColuna(data, nome)).toList();
        int indiceMetrica = metrica == null || metrica.isBlank() ? -1 : exigirColuna(data, metrica);
        String funcao = agregacao == null ? (indiceMetrica < 0 ? "contagem" : "soma") : agregacao.toLowerCase(Locale.ROOT);
        Map<List<Object>, List<Double>> grupos = new LinkedHashMap<>();
        for (List<Object> row : data.rows()) {
            List<Object> chave = new ArrayList<>();
            for (int indice : indicesDimensao) chave.add(row.get(indice));
            List<Double> valores = grupos.computeIfAbsent(chave, ignored -> new ArrayList<>());
            if (indiceMetrica < 0) valores.add(1.0);
            else {
                Double valor = numero(row.get(indiceMetrica));
                if (valor != null) valores.add(valor);
            }
        }
        List<TabularData.Column> colunas = new ArrayList<>();
        for (int indice : indicesDimensao) colunas.add(data.columns().get(indice));
        String nomeMetrica = indiceMetrica < 0 ? "quantidade" : funcao + "_" + data.columns().get(indiceMetrica).name();
        colunas.add(new TabularData.Column(nomeMetrica, TabularData.ColumnType.NUMERO));
        List<List<Object>> linhas = new ArrayList<>();
        grupos.forEach((chave, valores) -> {
            List<Object> linha = new ArrayList<>(chave);
            linha.add(arredondar(agregar(valores, funcao)));
            linhas.add(linha);
        });
        linhas.sort((a, b) -> Double.compare(numeroOuZero(b.getLast()), numeroOuZero(a.getLast())));
        return new TabularData(colunas, linhas, linhas.size(), false);
    }

    public static TabularData serieTemporal(TabularData data, String colunaData, String granularidade, String metrica,
            String agregacao, String dimensao) {
        int indiceData = exigirColuna(data, colunaData);
        String gran = granularidade == null ? "mes" : granularidade.toLowerCase(Locale.ROOT);
        List<List<Object>> base = new ArrayList<>();
        List<TabularData.Column> colunas = new ArrayList<>();
        colunas.add(new TabularData.Column("periodo", TabularData.ColumnType.TEXTO));
        colunas.addAll(data.columns());
        for (List<Object> row : data.rows()) {
            String periodo = periodo(row.get(indiceData), gran);
            if (periodo == null) continue;
            List<Object> linha = new ArrayList<>();
            linha.add(periodo);
            linha.addAll(row);
            base.add(linha);
        }
        TabularData expandido = new TabularData(colunas, base, base.size(), false);
        List<String> dimensoes = dimensao == null ? List.of("periodo") : List.of("periodo", dimensao);
        TabularData agrupado = agrupar(expandido, dimensoes, metrica, agregacao);
        List<List<Object>> ordenado = new ArrayList<>(agrupado.rows());
        ordenado.sort(Comparator.comparing(row -> String.valueOf(row.getFirst())));
        return new TabularData(agrupado.columns(), ordenado, ordenado.size(), false);
    }

    public static TabularData estatisticas(TabularData data) {
        List<TabularData.Column> colunas = List.of(
                new TabularData.Column("coluna", TabularData.ColumnType.TEXTO),
                new TabularData.Column("tipo", TabularData.ColumnType.TEXTO),
                new TabularData.Column("preenchidos", TabularData.ColumnType.INTEIRO),
                new TabularData.Column("distintos", TabularData.ColumnType.INTEIRO),
                new TabularData.Column("soma", TabularData.ColumnType.NUMERO),
                new TabularData.Column("media", TabularData.ColumnType.NUMERO),
                new TabularData.Column("mediana", TabularData.ColumnType.NUMERO),
                new TabularData.Column("desvio_padrao", TabularData.ColumnType.NUMERO),
                new TabularData.Column("minimo", TabularData.ColumnType.TEXTO),
                new TabularData.Column("maximo", TabularData.ColumnType.TEXTO),
                new TabularData.Column("valores_mais_frequentes", TabularData.ColumnType.TEXTO));
        List<List<Object>> linhas = new ArrayList<>();
        for (int i = 0; i < data.columns().size(); i++) {
            TabularData.Column coluna = data.columns().get(i);
            List<Object> valores = new ArrayList<>();
            for (List<Object> row : data.rows()) if (row.get(i) != null) valores.add(row.get(i));
            Set<String> distintos = new HashSet<>();
            valores.forEach(valor -> distintos.add(String.valueOf(valor)));
            boolean numerico = coluna.type() == TabularData.ColumnType.NUMERO || coluna.type() == TabularData.ColumnType.INTEIRO;
            List<Object> linha = new ArrayList<>(Arrays.asList(new Object[11]));
            linha.set(0, coluna.name());
            linha.set(1, coluna.type().name().toLowerCase(Locale.ROOT));
            linha.set(2, valores.size());
            linha.set(3, distintos.size());
            if (numerico && !valores.isEmpty()) {
                List<Double> numeros = valores.stream().map(TabularOps::numero).filter(Objects::nonNull).sorted().toList();
                double soma = numeros.stream().mapToDouble(Double::doubleValue).sum();
                double media = soma / numeros.size();
                double variancia = numeros.stream().mapToDouble(v -> (v - media) * (v - media)).sum() / Math.max(1, numeros.size() - 1);
                linha.set(4, arredondar(soma));
                linha.set(5, arredondar(media));
                linha.set(6, arredondar(mediana(numeros)));
                linha.set(7, arredondar(Math.sqrt(variancia)));
                linha.set(8, String.valueOf(arredondar(numeros.getFirst())));
                linha.set(9, String.valueOf(arredondar(numeros.getLast())));
            } else if (!valores.isEmpty()) {
                List<String> textos = valores.stream().map(String::valueOf).sorted().toList();
                linha.set(8, textos.getFirst());
                linha.set(9, textos.getLast());
                Map<String, Integer> frequencia = new HashMap<>();
                textos.forEach(texto -> frequencia.merge(texto, 1, Integer::sum));
                linha.set(10, frequencia.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                        .map(entry -> entry.getKey() + " (" + entry.getValue() + ")").reduce((a, b) -> a + "; " + b).orElse(""));
            }
            linhas.add(linha);
        }
        return new TabularData(colunas, linhas, linhas.size(), false);
    }

    /**
     * Concilia dois resultados pela chave: linhas presentes so em A, so em B, divergentes e iguais.
     * Chaves sao comparadas sem diferenciar maiusculas, acentos e espacos extras.
     */
    public static TabularData conciliar(TabularData a, String chaveA, List<String> metricasA,
            TabularData b, String chaveB, List<String> metricasB, String rotuloA, String rotuloB, double tolerancia) {
        int indiceChaveA = exigirColuna(a, chaveA);
        int indiceChaveB = exigirColuna(b, chaveB == null ? chaveA : chaveB);
        List<Integer> indicesA = metricasA.stream().map(nome -> exigirColuna(a, nome)).toList();
        List<String> nomesB = metricasB == null || metricasB.isEmpty() ? metricasA : metricasB;
        if (nomesB.size() != indicesA.size()) throw new ToolException("Informe o mesmo numero de metricas para A e B.");
        List<Integer> indicesB = nomesB.stream().map(nome -> exigirColuna(b, nome)).toList();

        Map<String, Object[]> mapaA = indexar(a, indiceChaveA, indicesA);
        Map<String, Object[]> mapaB = indexar(b, indiceChaveB, indicesB);
        LinkedHashSet<String> chaves = new LinkedHashSet<>(mapaA.keySet());
        chaves.addAll(mapaB.keySet());

        List<TabularData.Column> colunas = new ArrayList<>();
        colunas.add(new TabularData.Column("chave", TabularData.ColumnType.TEXTO));
        colunas.add(new TabularData.Column("situacao", TabularData.ColumnType.TEXTO));
        for (int i = 0; i < indicesA.size(); i++) {
            String nome = metricasA.get(i);
            colunas.add(new TabularData.Column(nome + "_" + rotuloA, TabularData.ColumnType.NUMERO));
            colunas.add(new TabularData.Column(nome + "_" + rotuloB, TabularData.ColumnType.NUMERO));
            colunas.add(new TabularData.Column("diferenca_" + nome, TabularData.ColumnType.NUMERO));
        }
        List<List<Object>> linhas = new ArrayList<>();
        for (String chave : chaves) {
            Object[] valoresA = mapaA.get(chave);
            Object[] valoresB = mapaB.get(chave);
            String situacao;
            List<Object> metricas = new ArrayList<>();
            boolean divergente = false;
            for (int i = 0; i < indicesA.size(); i++) {
                Double va = valoresA == null ? null : numero(valoresA[i + 1]);
                Double vb = valoresB == null ? null : numero(valoresB[i + 1]);
                metricas.add(va == null ? null : arredondar(va));
                metricas.add(vb == null ? null : arredondar(vb));
                Double diferenca = va == null || vb == null ? null : vb - va;
                metricas.add(diferenca == null ? null : arredondar(diferenca));
                if (diferenca != null && Math.abs(diferenca) > tolerancia) divergente = true;
            }
            if (valoresA == null) situacao = "SOMENTE_" + rotuloB.toUpperCase(Locale.ROOT);
            else if (valoresB == null) situacao = "SOMENTE_" + rotuloA.toUpperCase(Locale.ROOT);
            else situacao = divergente ? "DIVERGENTE" : "CONFERE";
            List<Object> linha = new ArrayList<>();
            linha.add(valoresA != null ? valoresA[0] : valoresB[0]);
            linha.add(situacao);
            linha.addAll(metricas);
            linhas.add(linha);
        }
        linhas.sort(Comparator.comparing((List<Object> row) -> ordemSituacao(String.valueOf(row.get(1))))
                .thenComparing(row -> String.valueOf(row.getFirst())));
        return new TabularData(colunas, linhas, linhas.size(), false);
    }

    /** Anomalias por IQR (Tukey, k=1.5) ou z-score (|z| >= limite). */
    public static TabularData anomalias(TabularData data, String metrica, String metodo, double limiteZ) {
        int indice = exigirColuna(data, metrica);
        List<Double> valores = new ArrayList<>();
        for (List<Object> row : data.rows()) {
            Double valor = numero(row.get(indice));
            if (valor != null) valores.add(valor);
        }
        if (valores.size() < 4) throw new ToolException("Sao necessarios ao menos 4 valores numericos para detectar anomalias.");
        List<Double> ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);
        double media = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double desvio = Math.sqrt(valores.stream().mapToDouble(v -> (v - media) * (v - media)).sum() / Math.max(1, valores.size() - 1));
        double q1 = quantil(ordenados, 0.25);
        double q3 = quantil(ordenados, 0.75);
        double iqr = q3 - q1;
        double inferior = q1 - 1.5 * iqr;
        double superior = q3 + 1.5 * iqr;
        boolean usarZ = "zscore".equalsIgnoreCase(metodo);

        List<TabularData.Column> colunas = new ArrayList<>(data.columns());
        colunas.add(new TabularData.Column("zscore", TabularData.ColumnType.NUMERO));
        colunas.add(new TabularData.Column("anomalia", TabularData.ColumnType.TEXTO));
        List<List<Object>> linhas = new ArrayList<>();
        for (List<Object> row : data.rows()) {
            Double valor = numero(row.get(indice));
            if (valor == null) continue;
            double z = desvio == 0 ? 0 : (valor - media) / desvio;
            String tipo = null;
            if (usarZ ? Math.abs(z) >= limiteZ : valor > superior) tipo = valor > media ? "ACIMA_DO_ESPERADO" : "ABAIXO_DO_ESPERADO";
            if (!usarZ && valor < inferior) tipo = "ABAIXO_DO_ESPERADO";
            if (tipo == null) continue;
            List<Object> linha = new ArrayList<>(row);
            linha.add(arredondar(z));
            linha.add(tipo);
            linhas.add(linha);
        }
        linhas.sort((x, y) -> Double.compare(Math.abs(numeroOuZero(y.get(y.size() - 2))), Math.abs(numeroOuZero(x.get(x.size() - 2)))));
        return new TabularData(colunas, linhas, linhas.size(), false);
    }

    public static Map<String, Object> resumoDistribuicao(TabularData data, String metrica) {
        int indice = exigirColuna(data, metrica);
        List<Double> valores = new ArrayList<>();
        for (List<Object> row : data.rows()) {
            Double valor = numero(row.get(indice));
            if (valor != null) valores.add(valor);
        }
        Collections.sort(valores);
        Map<String, Object> resumo = new LinkedHashMap<>();
        if (valores.isEmpty()) return resumo;
        double media = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        resumo.put("quantidade", valores.size());
        resumo.put("media", arredondar(media));
        resumo.put("mediana", arredondar(mediana(valores)));
        resumo.put("q1", arredondar(quantil(valores, 0.25)));
        resumo.put("q3", arredondar(quantil(valores, 0.75)));
        resumo.put("minimo", arredondar(valores.getFirst()));
        resumo.put("maximo", arredondar(valores.getLast()));
        return resumo;
    }

    public static TabularData filtrar(TabularData data, String coluna, String valor) {
        int indice = exigirColuna(data, coluna);
        String alvo = normalizar(valor);
        List<List<Object>> linhas = data.rows().stream()
                .filter(row -> row.get(indice) != null && normalizar(String.valueOf(row.get(indice))).contains(alvo))
                .toList();
        return new TabularData(data.columns(), linhas, linhas.size(), false);
    }

    // --------------------------------------------------------------------------------- utilitarios

    public static int exigirColuna(TabularData data, String nome) {
        if (nome == null) throw new ToolException("Coluna nao informada.");
        int indice = data.columnIndex(nome.trim());
        if (indice < 0) {
            throw new ToolException("Coluna '" + nome + "' nao existe no resultado. Colunas: "
                    + String.join(", ", data.columns().stream().map(TabularData.Column::name).toList()));
        }
        return indice;
    }

    public static Double numero(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Number number) return number.doubleValue();
        String texto = String.valueOf(valor).trim();
        if (texto.isEmpty()) return null;
        try {
            if (texto.matches("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?")) texto = texto.replace(".", "").replace(",", ".");
            else if (texto.matches("-?\\d+,\\d+")) texto = texto.replace(",", ".");
            return Double.valueOf(texto);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Object arredondar(double valor) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) return null;
        if (valor == Math.rint(valor) && Math.abs(valor) < 1e15) return (long) valor;
        return Math.round(valor * 10_000.0) / 10_000.0;
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object[]> indexar(TabularData data, int indiceChave, List<Integer> indicesMetricas) {
        Map<String, Object[]> mapa = new LinkedHashMap<>();
        for (List<Object> row : data.rows()) {
            Object chaveBruta = row.get(indiceChave);
            if (chaveBruta == null) continue;
            String chave = normalizar(String.valueOf(chaveBruta));
            if (chave.endsWith(".0")) chave = chave.substring(0, chave.length() - 2);
            Object[] existente = mapa.get(chave);
            if (existente == null) {
                Object[] valores = new Object[indicesMetricas.size() + 1];
                valores[0] = chaveBruta;
                for (int i = 0; i < indicesMetricas.size(); i++) valores[i + 1] = numero(row.get(indicesMetricas.get(i)));
                mapa.put(chave, valores);
            } else {
                for (int i = 0; i < indicesMetricas.size(); i++) {
                    Double atual = numero(existente[i + 1]);
                    Double novo = numero(row.get(indicesMetricas.get(i)));
                    existente[i + 1] = atual == null ? novo : novo == null ? atual : atual + novo;
                }
            }
        }
        return mapa;
    }

    private static int ordemSituacao(String situacao) {
        if (situacao.equals("DIVERGENTE")) return 0;
        if (situacao.startsWith("SOMENTE_")) return 1;
        return 2;
    }

    public static double agregar(List<Double> valores, String funcao) {
        if (valores.isEmpty()) return 0;
        return switch (funcao) {
            case "contagem" -> valores.size();
            case "media" -> valores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "minimo" -> valores.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "maximo" -> valores.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "mediana" -> mediana(valores.stream().sorted().toList());
            case "soma" -> valores.stream().mapToDouble(Double::doubleValue).sum();
            default -> throw new ToolException("Agregacao invalida: " + funcao + ". Use soma, media, contagem, minimo, maximo ou mediana.");
        };
    }

    private static double mediana(List<Double> ordenados) {
        return quantil(ordenados, 0.5);
    }

    private static double quantil(List<Double> ordenados, double q) {
        if (ordenados.isEmpty()) return 0;
        double posicao = (ordenados.size() - 1) * q;
        int base = (int) Math.floor(posicao);
        double resto = posicao - base;
        if (base + 1 < ordenados.size()) return ordenados.get(base) + resto * (ordenados.get(base + 1) - ordenados.get(base));
        return ordenados.get(base);
    }

    private static double numeroOuZero(Object valor) {
        Double numero = numero(valor);
        return numero == null ? 0 : numero;
    }

    public static String periodo(Object valor, String granularidade) {
        if (valor == null) return null;
        LocalDate data;
        try {
            String texto = String.valueOf(valor);
            data = texto.length() > 10 ? OffsetDateTime.parse(texto).toLocalDate() : LocalDate.parse(texto);
        } catch (RuntimeException exception) {
            try {
                data = LocalDate.parse(String.valueOf(valor).substring(0, 10));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return switch (granularidade) {
            case "dia" -> data.toString();
            case "semana" -> data.get(IsoFields.WEEK_BASED_YEAR) + "-S" + String.format("%02d", data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "ano" -> String.valueOf(data.getYear());
            default -> data.getYear() + "-" + String.format("%02d", data.getMonthValue());
        };
    }
}
