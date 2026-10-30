package com.nexo.app.modules.automacao.domain.definicao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

/**
 * Definição de uma automação (versão 3 do formato): um gatilho, variáveis declaradas,
 * uma árvore de passos e configurações.
 *
 * <p>O formato JSON é o mesmo do motor do TON — {@code trigger}, {@code variables},
 * {@code steps}, {@code settings}, {@code layout} — para que o designer, o rascunho por
 * IA e as validações falem a mesma língua. Aqui fica só a <em>estrutura</em> (ids
 * únicos, limites, profundidade); o <em>significado</em> (parâmetros, referências
 * entre passos) é do verificador, que conhece o catálogo de nós.</p>
 */
public record DefinicaoAutomacao(
        Gatilho gatilho,
        List<Variavel> variaveis,
        List<No> passos,
        Configuracoes configuracoes,
        Map<String, Object> layout
) {

    public static final int MAX_NOS = 150;
    public static final int MAX_PROFUNDIDADE = 8;
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");

    public record Gatilho(String tipo, Map<String, Object> params) {
    }

    public record Variavel(String nome, String tipo, Object valor, String descricao) {
    }

    public record Configuracoes(List<String> avisarFalha, int limiteHoras) {
    }

    public record PoliticaRepeticao(String politica, int quantidade, int intervaloSegundos) {

        public static final PoliticaRepeticao NENHUMA = new PoliticaRepeticao("none", 0, 30);

        public long atraso(int tentativa) {
            if ("exponential".equals(politica)) {
                return Math.min(3600, (long) intervaloSegundos * (1L << Math.min(20, tentativa - 1)));
            }
            return intervaloSegundos;
        }
    }

    public record Caso(String id, String valor, List<No> passos) {
    }

    public record Ramo(String id, String rotulo, List<No> passos) {
    }

    /** Um passo; os contêineres usam as listas filhas conforme o tipo. */
    public record No(
            String id,
            String tipo,
            String rotulo,
            String descricao,
            Map<String, Object> params,
            List<String> executarApos,
            PoliticaRepeticao repeticao,
            Integer limiteSegundos,
            List<No> entao,
            List<No> senao,
            List<Caso> casos,
            List<No> padrao,
            List<No> passos,
            List<Ramo> ramos
    ) {

        /** (slot, lista) de cada lista filha, ex.: ("then", [...]). */
        public List<Map.Entry<String, List<No>>> filhos() {
            List<Map.Entry<String, List<No>>> filhos = new ArrayList<>();
            if (entao != null) filhos.add(Map.entry("then", entao));
            if (senao != null) filhos.add(Map.entry("else", senao));
            if (casos != null) for (Caso caso : casos) filhos.add(Map.entry("case:" + caso.id(), caso.passos()));
            if (padrao != null) filhos.add(Map.entry("default", padrao));
            if (passos != null) filhos.add(Map.entry("steps", passos));
            if (ramos != null) for (Ramo ramo : ramos) filhos.add(Map.entry("branch:" + ramo.id(), ramo.passos()));
            return filhos;
        }

        public String nomeExibicao() {
            return rotulo == null || rotulo.isBlank() ? id : rotulo;
        }
    }

    // ------------------------------------------------------------------------------ navegação

    public static void percorrer(List<No> nos, Consumer<No> visitante) {
        for (No no : nos) {
            visitante.accept(no);
            for (Map.Entry<String, List<No>> filho : no.filhos()) percorrer(filho.getValue(), visitante);
        }
    }

    public List<No> todosOsNos() {
        List<No> todos = new ArrayList<>();
        percorrer(passos, todos::add);
        return todos;
    }

    public No buscar(String id) {
        for (No no : todosOsNos()) if (no.id().equals(id)) return no;
        return null;
    }

    // ------------------------------------------------------------------------------ leitura

    @SuppressWarnings("unchecked")
    public static DefinicaoAutomacao ler(Object bruto) {
        if (!(bruto instanceof Map<?, ?> mapa)) throw new InvarianteVioladaException("A definição da automação está vazia.");
        Map<String, Object> raiz = Valores.mapa(mapa);
        Object schema = raiz.getOrDefault("schema", 3);
        if (Valores.dbl(schema) != 3) throw new InvarianteVioladaException("Formato de automação desconhecido: " + schema);

        Map<String, Object> gatilhoBruto = Valores.mapa(raiz.get("trigger"));
        String tipoGatilho = Valores.texto(gatilhoBruto.get("type")).strip();
        if (tipoGatilho.isEmpty()) throw new InvarianteVioladaException("Escolha o gatilho da automação.");
        Gatilho gatilho = new Gatilho(tipoGatilho, Valores.mapa(gatilhoBruto.get("params")));

        List<Variavel> variaveis = new ArrayList<>();
        Set<String> nomes = new HashSet<>();
        for (Object item : Valores.lista(raiz.get("variables"))) {
            Map<String, Object> v = Valores.mapa(item);
            String nome = Valores.texto(v.get("name")).strip().toLowerCase();
            if (!ID.matcher(nome).matches()) {
                throw new InvarianteVioladaException("Nome de variável inválido '" + nome + "': use letras minúsculas, números e _ (ex.: prazo_correcao).");
            }
            if (!nomes.add(nome)) throw new InvarianteVioladaException("Há duas variáveis com o nome '" + nome + "'.");
            String tipo = Valores.texto(v.getOrDefault("type", "string"));
            if (!List.of("string", "number", "boolean", "array", "object").contains(tipo)) tipo = "string";
            variaveis.add(new Variavel(nome, tipo, v.get("value"), limitar(Valores.texto(v.get("description")), 300)));
        }

        Set<String> ids = new HashSet<>();
        int[] contagem = {0};
        List<No> passos = lerLista(raiz.get("steps"), ids, contagem, 1);
        if (contagem[0] > MAX_NOS) throw new InvarianteVioladaException("No máximo " + MAX_NOS + " passos por automação.");

        Map<String, Object> cfg = Valores.mapa(raiz.get("settings"));
        List<String> avisar = new ArrayList<>();
        for (Object e : Valores.lista(cfg.get("notify_on_failure"))) {
            String email = Valores.texto(e).strip();
            if (!email.isEmpty()) avisar.add(email);
        }
        int horas = (int) Math.max(1, Math.min(720, Valores.dbl(cfg.getOrDefault("timeout_hours", 168))));
        Configuracoes configuracoes = new Configuracoes(avisar, horas);

        Map<String, Object> layout = new LinkedHashMap<>();
        Set<String> conhecidos = new HashSet<>(ids);
        conhecidos.add("trigger");
        Valores.mapa(raiz.get("layout")).forEach((chave, valor) -> {
            if (conhecidos.contains(chave) && valor instanceof Map<?, ?> pos) {
                Map<String, Object> p = Valores.mapa(pos);
                layout.put(chave, Map.of("x", limitarCoordenada(p.get("x")), "y", limitarCoordenada(p.get("y"))));
            }
        });
        return new DefinicaoAutomacao(gatilho, List.copyOf(variaveis), passos, configuracoes, layout);
    }

    private static double limitarCoordenada(Object valor) {
        double d = Valores.dbl(valor);
        return Math.round(Math.max(-20000, Math.min(20000, d)) * 10) / 10.0;
    }

    private static List<No> lerLista(Object bruto, Set<String> ids, int[] contagem, int profundidade) {
        if (profundidade > MAX_PROFUNDIDADE) {
            throw new InvarianteVioladaException("No máximo " + MAX_PROFUNDIDADE + " níveis de passos.");
        }
        List<No> nos = new ArrayList<>();
        for (Object item : Valores.lista(bruto)) {
            if (item instanceof Map<?, ?> mapa) nos.add(lerNo(Valores.mapa(mapa), ids, contagem, profundidade));
        }
        return List.copyOf(nos);
    }

    private static No lerNo(Map<String, Object> m, Set<String> ids, int[] contagem, int profundidade) {
        contagem[0]++;
        String id = Valores.texto(m.get("id")).strip();
        if (!ID.matcher(id).matches()) {
            throw new InvarianteVioladaException("Id de passo inválido '" + id + "': use letras minúsculas, números e _.");
        }
        if (id.equals("trigger") || !ids.add(id)) throw new InvarianteVioladaException("Há dois passos com o id '" + id + "'.");
        String tipo = Valores.texto(m.get("type")).strip();
        if (tipo.isEmpty() || tipo.length() > 64) throw new InvarianteVioladaException("O passo '" + id + "' não tem tipo.");

        List<String> apos = new ArrayList<>();
        Object runAfter = m.get("run_after");
        if (runAfter == null) {
            apos.add("succeeded");
        } else {
            for (Object s : Valores.lista(runAfter)) {
                String status = Valores.texto(s);
                if (!List.of("succeeded", "failed", "skipped", "timed_out").contains(status)) {
                    throw new InvarianteVioladaException("Situação inválida em 'Executar após': " + status);
                }
                if (!apos.contains(status)) apos.add(status);
            }
            if (apos.isEmpty()) throw new InvarianteVioladaException("Escolha ao menos uma situação em 'Executar após' (" + id + ").");
        }

        PoliticaRepeticao repeticao = null;
        if (m.get("retry") instanceof Map<?, ?> r) {
            Map<String, Object> retry = Valores.mapa(r);
            String politica = Valores.texto(retry.getOrDefault("policy", "none"));
            if (!List.of("none", "fixed", "exponential").contains(politica)) politica = "none";
            int quantidade = politica.equals("none") ? 0 : (int) Math.max(0, Math.min(10, Valores.dbl(retry.get("count"))));
            int intervalo = (int) Math.max(1, Math.min(3600, Valores.dbl(retry.getOrDefault("interval_seconds", 30))));
            repeticao = new PoliticaRepeticao(politica, quantidade, intervalo);
        }
        Integer limite = m.get("timeout_seconds") == null ? null
                : (int) Math.max(1, Math.min(86_400, Valores.dbl(m.get("timeout_seconds"))));

        List<Caso> casos = null;
        if (m.get("cases") != null) {
            casos = new ArrayList<>();
            for (Object c : Valores.lista(m.get("cases"))) {
                Map<String, Object> caso = Valores.mapa(c);
                casos.add(new Caso(limitar(Valores.texto(caso.get("id")), 40), limitar(Valores.texto(caso.get("value")), 200),
                        lerLista(caso.get("steps"), ids, contagem, profundidade + 1)));
            }
        }
        List<Ramo> ramos = null;
        if (m.get("branches") != null) {
            ramos = new ArrayList<>();
            for (Object b : Valores.lista(m.get("branches"))) {
                Map<String, Object> ramo = Valores.mapa(b);
                ramos.add(new Ramo(limitar(Valores.texto(ramo.get("id")), 40), limitar(Valores.texto(ramo.get("label")), 80),
                        lerLista(ramo.get("steps"), ids, contagem, profundidade + 1)));
            }
        }
        return new No(id, tipo, limitar(Valores.texto(m.get("label")), 120), limitar(Valores.texto(m.get("description")), 500),
                Valores.mapa(m.get("params")), List.copyOf(apos), repeticao, limite,
                m.containsKey("then") ? lerLista(m.get("then"), ids, contagem, profundidade + 1) : null,
                m.containsKey("else") ? lerLista(m.get("else"), ids, contagem, profundidade + 1) : null,
                casos == null ? null : List.copyOf(casos),
                m.containsKey("default") ? lerLista(m.get("default"), ids, contagem, profundidade + 1) : null,
                m.containsKey("steps") ? lerLista(m.get("steps"), ids, contagem, profundidade + 1) : null,
                ramos == null ? null : List.copyOf(ramos));
    }

    private static String limitar(String texto, int limite) {
        String t = texto == null ? "" : texto.strip();
        return t.length() > limite ? t.substring(0, limite) : t;
    }

    // ------------------------------------------------------------------------------ escrita

    public Map<String, Object> paraMapa() {
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("schema", 3);
        raiz.put("trigger", new LinkedHashMap<>(Map.of("type", gatilho.tipo(), "params", gatilho.params())));
        List<Object> vars = new ArrayList<>();
        for (Variavel v : variaveis) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.nome());
            m.put("type", v.tipo());
            m.put("value", v.valor());
            if (!v.descricao().isEmpty()) m.put("description", v.descricao());
            vars.add(m);
        }
        raiz.put("variables", vars);
        raiz.put("steps", escreverLista(passos));
        raiz.put("settings", new LinkedHashMap<>(Map.of(
                "notify_on_failure", configuracoes.avisarFalha(), "timeout_hours", configuracoes.limiteHoras())));
        raiz.put("layout", layout);
        return raiz;
    }

    private static List<Object> escreverLista(Collection<No> nos) {
        List<Object> lista = new ArrayList<>();
        for (No no : nos) lista.add(escreverNo(no));
        return lista;
    }

    private static Map<String, Object> escreverNo(No no) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", no.id());
        m.put("type", no.tipo());
        m.put("label", no.rotulo());
        if (!no.descricao().isEmpty()) m.put("description", no.descricao());
        m.put("params", no.params());
        if (!no.executarApos().equals(List.of("succeeded"))) m.put("run_after", no.executarApos());
        if (no.repeticao() != null) {
            m.put("retry", Map.of("policy", no.repeticao().politica(), "count", no.repeticao().quantidade(),
                    "interval_seconds", no.repeticao().intervaloSegundos()));
        }
        if (no.limiteSegundos() != null) m.put("timeout_seconds", no.limiteSegundos());
        if (no.entao() != null) m.put("then", escreverLista(no.entao()));
        if (no.senao() != null) m.put("else", escreverLista(no.senao()));
        if (no.casos() != null) {
            List<Object> casos = new ArrayList<>();
            for (Caso c : no.casos()) casos.add(Map.of("id", c.id(), "value", c.valor(), "steps", escreverLista(c.passos())));
            m.put("cases", casos);
        }
        if (no.padrao() != null) m.put("default", escreverLista(no.padrao()));
        if (no.passos() != null) m.put("steps", escreverLista(no.passos()));
        if (no.ramos() != null) {
            List<Object> ramos = new ArrayList<>();
            for (Ramo r : no.ramos()) ramos.add(Map.of("id", r.id(), "label", r.rotulo(), "steps", escreverLista(r.passos())));
            m.put("branches", ramos);
        }
        return m;
    }

    /** "Desconto validado" → "desconto_validado", único entre os já usados. */
    public static String idUnico(String base, Set<String> usados) {
        String slug = java.text.Normalizer.normalize(base == null ? "" : base, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > 34) slug = slug.substring(0, 34);
        if (slug.isEmpty()) slug = "passo";
        if (!Character.isLetter(slug.charAt(0))) slug = ("p_" + slug).substring(0, Math.min(34, slug.length() + 2));
        String candidato = slug;
        int i = 2;
        while (usados.contains(candidato)) candidato = slug + "_" + i++;
        usados.add(candidato);
        return candidato;
    }
}
