package com.nexo.app.modules.assistente.application.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contexto estruturado de um painel (dashboard) aberto pelo usuario: identidade, periodo, filtros, KPIs,
 * graficos com as agregacoes visiveis, entidades selecionadas e datasets relacionados.
 *
 * <p>O contrato e generico — nenhum painel tem regra propria no agente. Tudo vem do frontend e e tratado
 * como dado nao confiavel: textos sao saneados e limitados, listas sao truncadas e nada aqui autoriza
 * acesso a dados (as ferramentas continuam validando permissoes a cada consulta). O objetivo e dizer ao
 * agente <em>onde olhar</em>; o detalhe continua vindo das ferramentas.</p>
 */
public record ContextoPainel(
        String id,
        String nome,
        String modulo,
        String descricao,
        Periodo periodo,
        List<Filtro> filtros,
        List<Metrica> metricas,
        List<Grafico> graficos,
        List<Entidade> entidades,
        List<String> datasets,
        Foco foco,
        Map<String, String> extras,
        List<Tabela> tabelas) {

    static final int MAX_FILTROS = 30;
    static final int MAX_METRICAS = 40;
    static final int MAX_GRAFICOS = 16;
    static final int MAX_PONTOS = 40;
    static final int MAX_ENTIDADES = 20;
    static final int MAX_DATASETS = 16;
    static final int MAX_EXTRAS = 20;
    static final int MAX_TABELAS = 6;
    static final int MAX_COLUNAS = 12;

    /** Contexto sem tabelas filtraveis (paineis que ainda nao declaram {@link Tabela}). */
    public ContextoPainel(String id, String nome, String modulo, String descricao, Periodo periodo, List<Filtro> filtros,
            List<Metrica> metricas, List<Grafico> graficos, List<Entidade> entidades, List<String> datasets, Foco foco,
            Map<String, String> extras) {
        this(id, nome, modulo, descricao, periodo, filtros, metricas, graficos, entidades, datasets, foco, extras, null);
    }

    public ContextoPainel {
        id = Texto.limitar(id, 80);
        nome = Texto.limitar(nome, 120);
        modulo = Texto.limitar(modulo, 80);
        descricao = Texto.limitar(descricao, 400);
        filtros = Texto.lista(filtros, MAX_FILTROS);
        metricas = Texto.lista(metricas, MAX_METRICAS);
        graficos = Texto.lista(graficos, MAX_GRAFICOS);
        entidades = Texto.lista(entidades, MAX_ENTIDADES);
        datasets = datasets == null ? List.of() : datasets.stream().filter(Objects::nonNull)
                .map(valor -> Texto.limitar(valor, 60)).filter(Objects::nonNull).distinct().limit(MAX_DATASETS).toList();
        extras = Texto.mapa(extras, MAX_EXTRAS);
        tabelas = Texto.lista(tabelas, MAX_TABELAS).stream().filter(tabela -> tabela.id() != null && !tabela.colunas().isEmpty())
                .toList();
    }

    /** Tabela da tela pelo id declarado (alvo de {@code APPLY_FILTER}). */
    public java.util.Optional<Tabela> tabela(String id) {
        return tabelas.stream().filter(tabela -> tabela.id().equals(id)).findFirst();
    }

    public boolean vazio() {
        return (id == null || id.isBlank()) && (nome == null || nome.isBlank());
    }

    /** Resumo de uma linha do recorte (periodo, filtros e foco) — usado para marcar cada pergunta no historico. */
    public String assinatura() {
        List<String> partes = new ArrayList<>();
        if (nome != null) partes.add("painel \"" + nome + "\"");
        if (periodo != null && periodo.descricao() != null) partes.add("período " + periodo.descricao());
        List<String> ativos = filtros.stream().filter(filtro -> filtro.valor() != null && !filtro.valor().isBlank())
                .map(filtro -> (filtro.rotulo() != null ? filtro.rotulo() : filtro.chave()) + ": " + filtro.valor()).toList();
        partes.add(ativos.isEmpty() ? "sem filtros" : "filtros " + String.join("; ", ativos));
        if (!entidades.isEmpty()) {
            partes.add("seleção " + String.join(", ", entidades.stream().map(Entidade::descricao).toList()));
        }
        if (foco != null && foco.descricao() != null) partes.add("foco " + foco.descricao());
        return String.join(" · ", partes);
    }

    public record Periodo(String inicio, String fim, String rotulo) {
        public Periodo {
            inicio = Texto.limitar(inicio, 40);
            fim = Texto.limitar(fim, 40);
            rotulo = Texto.limitar(rotulo, 80);
        }

        public String descricao() {
            if (rotulo != null && !rotulo.isBlank()) {
                return inicio != null || fim != null ? rotulo + " (" + intervalo() + ")" : rotulo;
            }
            return inicio == null && fim == null ? null : intervalo();
        }

        private String intervalo() {
            return (inicio == null ? "…" : inicio) + " a " + (fim == null ? "…" : fim);
        }
    }

    /** Filtro da tela: {@code valor} e o texto exibido; {@code valorId} o identificador usado nas consultas. */
    public record Filtro(String chave, String rotulo, String valor, String valorId) {
        public Filtro {
            chave = Texto.limitar(chave, 60);
            rotulo = Texto.limitar(rotulo, 80);
            valor = Texto.limitar(valor, 200);
            valorId = Texto.limitar(valorId, 200);
        }
    }

    public record Metrica(String id, String rotulo, Double valor, String valorFormatado, String unidade, String descricao,
            Comparacao comparacao) {
        public Metrica {
            id = Texto.limitar(id, 60);
            rotulo = Texto.limitar(rotulo, 120);
            valor = Texto.numero(valor);
            valorFormatado = Texto.limitar(valorFormatado, 60);
            unidade = Texto.limitar(unidade, 30);
            descricao = Texto.limitar(descricao, 240);
        }
    }

    /** Comparacao exibida no KPI (ex.: periodo anterior). {@code melhorQuando}: "maior" ou "menor". */
    public record Comparacao(String rotulo, Double valorAnterior, Double variacaoPercentual, String melhorQuando) {
        public Comparacao {
            rotulo = Texto.limitar(rotulo, 120);
            valorAnterior = Texto.numero(valorAnterior);
            variacaoPercentual = Texto.numero(variacaoPercentual);
            melhorQuando = Texto.limitar(melhorQuando, 10);
        }
    }

    /**
     * Grafico visivel: metrica por dimensao e apenas as agregacoes desenhadas (limitadas a {@link #MAX_PONTOS});
     * {@code totalCategorias} informa quantas existiam para o agente saber que ha mais dados nas ferramentas.
     */
    public record Grafico(String id, String tipo, String titulo, String metrica, String dimensao, String formato,
            List<Ponto> dados, Integer totalCategorias) {
        public Grafico {
            id = Texto.limitar(id, 60);
            tipo = Texto.limitar(tipo, 30);
            titulo = Texto.limitar(titulo, 160);
            metrica = Texto.limitar(metrica, 120);
            dimensao = Texto.limitar(dimensao, 120);
            formato = Texto.limitar(formato, 20);
            List<Ponto> originais = dados == null ? List.of() : dados.stream().filter(Objects::nonNull).toList();
            if (totalCategorias == null || totalCategorias < originais.size()) totalCategorias = originais.size();
            dados = originais.size() > MAX_PONTOS ? List.copyOf(originais.subList(0, MAX_PONTOS)) : originais;
        }
    }

    public record Ponto(String rotulo, Double valor, String serie) {
        public Ponto {
            rotulo = Texto.limitar(rotulo, 120);
            valor = Texto.numero(valor);
            serie = Texto.limitar(serie, 80);
        }
    }

    public record Entidade(String tipo, String id, String rotulo) {
        public Entidade {
            tipo = Texto.limitar(tipo, 60);
            id = Texto.limitar(id, 80);
            rotulo = Texto.limitar(rotulo, 160);
        }

        String descricao() {
            String nomeEntidade = rotulo != null ? rotulo : id;
            return (tipo != null ? tipo + " " : "") + "\"" + nomeEntidade + "\"" + (id != null && rotulo != null ? " (id " + id + ")" : "");
        }
    }

    /** Elemento especifico que o usuario pediu para analisar (grafico, metrica ou tabela do painel). */
    public record Foco(String tipo, String id, String rotulo) {
        public Foco {
            tipo = Texto.limitar(tipo, 30);
            id = Texto.limitar(id, 60);
            rotulo = Texto.limitar(rotulo, 160);
        }

        String descricao() {
            if (rotulo == null && id == null) return null;
            return (tipo != null ? tipo + " " : "") + "\"" + (rotulo != null ? rotulo : id) + "\"";
        }
    }

    /**
     * Tabela da tela que aceita filtros propostos pelo agente ({@code APPLY_FILTER}). So os campos e operadores
     * declarados aqui podem ser propostos; o frontend aplica apenas depois do clique do usuario.
     */
    public record Tabela(String id, String rotulo, List<Coluna> colunas) {
        public Tabela {
            id = id == null || !id.matches("[A-Za-z0-9._#-]{1,80}") ? null : id;
            rotulo = Texto.limitar(rotulo, 120);
            colunas = Texto.lista(colunas, MAX_COLUNAS).stream().filter(coluna -> coluna.campo() != null && coluna.tipo() != null)
                    .toList();
        }

        public java.util.Optional<Coluna> coluna(String campo) {
            return colunas.stream().filter(coluna -> coluna.campo().equals(campo)).findFirst();
        }
    }

    /** Coluna filtravel: {@code tipo} numero, percentual, texto ou booleano; operadores restritos ao tipo. */
    public record Coluna(String campo, String rotulo, String tipo, List<String> operadores) {
        static final List<String> TIPOS = List.of("numero", "percentual", "texto", "booleano");
        static final Map<String, List<String>> OPERADORES_POR_TIPO = Map.of(
                "numero", List.of("eq", "ne", "lt", "lte", "gt", "gte"),
                "percentual", List.of("eq", "ne", "lt", "lte", "gt", "gte"),
                "texto", List.of("eq", "contains"),
                "booleano", List.of("eq"));

        public Coluna {
            campo = campo == null || !campo.matches("[A-Za-z][A-Za-z0-9_]{0,59}") ? null : campo;
            rotulo = Texto.limitar(rotulo, 80);
            tipo = tipo != null && TIPOS.contains(tipo) ? tipo : null;
            List<String> permitidos = tipo == null ? List.of() : OPERADORES_POR_TIPO.get(tipo);
            operadores = operadores == null || operadores.isEmpty() ? permitidos
                    : operadores.stream().filter(permitidos::contains).distinct().toList();
        }
    }

    /** Saneamento comum: remove controles, limita tamanho, descarta numeros nao finitos e trunca colecoes. */
    static final class Texto {
        private Texto() {
        }

        static String limitar(String value, int max) {
            if (value == null) return null;
            String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s{2,}", " ").trim();
            if (cleaned.isEmpty()) return null;
            return cleaned.length() <= max ? cleaned : cleaned.substring(0, max - 1) + "…";
        }

        static Double numero(Double value) {
            return value == null || value.isNaN() || value.isInfinite() ? null : value;
        }

        static <T> List<T> lista(List<T> value, int max) {
            if (value == null) return List.of();
            return value.stream().filter(Objects::nonNull).limit(max).toList();
        }

        static Map<String, String> mapa(Map<String, String> value, int max) {
            if (value == null || value.isEmpty()) return Map.of();
            Map<String, String> limpo = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : value.entrySet()) {
                if (limpo.size() >= max) break;
                String chave = limitar(entry.getKey(), 60);
                String valor = limitar(entry.getValue(), 300);
                if (chave != null && valor != null) limpo.put(chave, valor);
            }
            return java.util.Collections.unmodifiableMap(limpo);
        }
    }
}
