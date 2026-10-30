package com.nexo.app.modules.automacao.application.motor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.PoliticaRepeticao;

/**
 * O catálogo de nós: todo gatilho e todo passo que uma automação pode usar.
 *
 * <p>Um tipo de nó é um {@link EspecNo} (rótulos, parâmetros, saídas, tipo de
 * contêiner, repetição padrão) e, para ações, um executor. O designer desenha os
 * formulários e o seletor de conteúdo dinâmico a partir daqui, o verificador valida
 * contra estas especificações e o interpretador as executa. Acrescentar um nó é
 * acrescentar um {@link ProvedorNos} — nenhuma outra classe muda.</p>
 */
@Component
public class Catalogo {

    public static final Map<String, String> GRUPOS = new LinkedHashMap<>();

    static {
        GRUPOS.put("trigger", "Gatilhos");
        GRUPOS.put("control", "Controle");
        GRUPOS.put("variables", "Valores guardados");
        GRUPOS.put("data", "Dados");
        GRUPOS.put("ces", "Nexo");
        GRUPOS.put("people", "Pessoas e destinatários");
        GRUPOS.put("ai", "Inteligência artificial");
        GRUPOS.put("notify", "Avisos e e-mail");
        GRUPOS.put("approval", "Aprovações");
        GRUPOS.put("integration", "Integrações");
    }

    /** Uma coluna de um parâmetro em lista (campos, mapeamento, colunas). */
    public record EspecCampo(String chave, String rotulo, String tipo, List<List<String>> opcoes, String placeholder) {

        public static EspecCampo texto(String chave, String rotulo, String placeholder) {
            return new EspecCampo(chave, rotulo, "text", List.of(), placeholder);
        }

        public static EspecCampo expressao(String chave, String rotulo, String placeholder) {
            return new EspecCampo(chave, rotulo, "expression", List.of(), placeholder);
        }

        public static EspecCampo selecao(String chave, String rotulo, List<List<String>> opcoes) {
            return new EspecCampo(chave, rotulo, "select", opcoes, null);
        }

        public static EspecCampo booleano(String chave, String rotulo) {
            return new EspecCampo(chave, rotulo, "boolean", List.of(), null);
        }
    }

    /**
     * Um parâmetro de nó.
     *
     * @param tipo        text, textarea, number, boolean, select, multiselect, emails, condition,
     *                    html, json, fields, mapping, columns, list, time, weekdays, expression,
     *                    recipients, routes, automations
     * @param resolver    falso quando o próprio executor resolve (por item, condições)
     * @param dinamico    aceita conteúdo dinâmico {{ }}
     * @param mostrarSe   [outro parâmetro, valores...] — só aparece quando o outro tem um dos valores
     */
    public record EspecParam(
            String chave, String rotulo, String tipo, boolean obrigatorio, Object padrao, String ajuda,
            String placeholder, List<List<String>> opcoes, boolean resolver, boolean dinamico, Double minimo,
            Double maximo, List<EspecCampo> campos, List<String> mostrarSe, boolean avancado) {

        public static Builder de(String chave, String rotulo, String tipo) {
            return new Builder(chave, rotulo, tipo);
        }

        public static final class Builder {
            private final String chave;
            private final String rotulo;
            private final String tipo;
            private boolean obrigatorio;
            private Object padrao;
            private String ajuda;
            private String placeholder;
            private List<List<String>> opcoes = List.of();
            private boolean resolver = true;
            private boolean dinamico = true;
            private Double minimo;
            private Double maximo;
            private List<EspecCampo> campos = List.of();
            private List<String> mostrarSe;
            private boolean avancado;

            Builder(String chave, String rotulo, String tipo) {
                this.chave = chave;
                this.rotulo = rotulo;
                this.tipo = tipo;
            }

            public Builder obrigatorio() {
                this.obrigatorio = true;
                return this;
            }

            public Builder padrao(Object valor) {
                this.padrao = valor;
                return this;
            }

            public Builder ajuda(String texto) {
                this.ajuda = texto;
                return this;
            }

            public Builder placeholder(String texto) {
                this.placeholder = texto;
                return this;
            }

            public Builder opcoes(List<List<String>> lista) {
                this.opcoes = lista;
                return this;
            }

            public Builder semResolver() {
                this.resolver = false;
                return this;
            }

            public Builder estatico() {
                this.dinamico = false;
                return this;
            }

            public Builder faixa(double min, double max) {
                this.minimo = min;
                this.maximo = max;
                return this;
            }

            public Builder campos(EspecCampo... lista) {
                this.campos = List.of(lista);
                return this;
            }

            public Builder mostrarSe(String outro, String... valores) {
                List<String> condicao = new ArrayList<>();
                condicao.add(outro);
                condicao.addAll(List.of(valores));
                this.mostrarSe = condicao;
                return this;
            }

            public Builder avancado() {
                this.avancado = true;
                return this;
            }

            public EspecParam criar() {
                return new EspecParam(chave, rotulo, tipo, obrigatorio, padrao, ajuda, placeholder, opcoes, resolver,
                        dinamico, minimo, maximo, campos, mostrarSe, avancado);
            }
        }
    }

    /** Uma saída de nó. Em listas de objetos, {@code campos} descreve cada item ({{ item.campo }}). */
    public record EspecSaida(String chave, String rotulo, String tipo, String descricao, List<EspecSaida> campos) {

        public static EspecSaida de(String chave, String rotulo, String tipo) {
            return new EspecSaida(chave, rotulo, tipo, "", List.of());
        }

        public static EspecSaida de(String chave, String rotulo, String tipo, String descricao) {
            return new EspecSaida(chave, rotulo, tipo, descricao, List.of());
        }

        public static EspecSaida lista(String chave, String rotulo, List<EspecSaida> campos) {
            return new EspecSaida(chave, rotulo, "array", "", campos);
        }

        public static EspecSaida objeto(String chave, String rotulo, List<EspecSaida> campos) {
            return new EspecSaida(chave, rotulo, "object", "", campos);
        }
    }

    @FunctionalInterface
    public interface Executor {
        Map<String, Object> executar(ContextoAcao contexto, Map<String, Object> params);
    }

    /** Monta a saída congelada do gatilho a partir do que o disparou. Nulo = não executar. */
    @FunctionalInterface
    public interface Preparador {
        Map<String, Object> preparar(ContextoGatilho contexto, Map<String, Object> params, Map<String, Object> carga);
    }

    public record EspecNo(
            String tipo, String grupo, String rotulo, String descricao, String icone, List<EspecParam> params,
            List<EspecSaida> saidas, String conteiner, boolean gatilho, boolean efeitoExterno,
            PoliticaRepeticao repeticaoPadrao, List<TipoAutomacao> satisfaz, Executor executor, Preparador preparador,
            String saidasDinamicas, boolean ia, List<String> palavrasChave, Function<Map<String, Object>, String> resumo,
            String chaveEvento, String subgrupo) {

        public EspecParam param(String chave) {
            return params.stream().filter(p -> p.chave().equals(chave)).findFirst().orElse(null);
        }

        public static Builder de(String tipo, String grupo, String rotulo, String descricao, String icone) {
            return new Builder(tipo, grupo, rotulo, descricao, icone);
        }

        public static final class Builder {
            private final String tipo;
            private final String grupo;
            private final String rotulo;
            private final String descricao;
            private final String icone;
            private List<EspecParam> params = List.of();
            private List<EspecSaida> saidas = List.of();
            private String conteiner;
            private boolean gatilho;
            private boolean efeitoExterno;
            private PoliticaRepeticao repeticaoPadrao = PoliticaRepeticao.NENHUMA;
            private List<TipoAutomacao> satisfaz = List.of();
            private Executor executor;
            private Preparador preparador;
            private String saidasDinamicas;
            private boolean ia;
            private List<String> palavrasChave = List.of();
            private Function<Map<String, Object>, String> resumo;
            private String chaveEvento;
            private String subgrupo;

            Builder(String tipo, String grupo, String rotulo, String descricao, String icone) {
                this.tipo = tipo;
                this.grupo = grupo;
                this.rotulo = rotulo;
                this.descricao = descricao;
                this.icone = icone;
            }

            public Builder params(EspecParam... lista) {
                this.params = List.of(lista);
                return this;
            }

            public Builder params(List<EspecParam> lista) {
                this.params = List.copyOf(lista);
                return this;
            }

            public Builder saidas(EspecSaida... lista) {
                this.saidas = List.of(lista);
                return this;
            }

            public Builder saidas(List<EspecSaida> lista) {
                this.saidas = List.copyOf(lista);
                return this;
            }

            public Builder conteiner(String tipoConteiner) {
                this.conteiner = tipoConteiner;
                return this;
            }

            public Builder gatilho() {
                this.gatilho = true;
                return this;
            }

            public Builder efeitoExterno() {
                this.efeitoExterno = true;
                return this;
            }

            public Builder repeticao(String politica, int quantidade, int intervalo) {
                this.repeticaoPadrao = new PoliticaRepeticao(politica, quantidade, intervalo);
                return this;
            }

            public Builder satisfaz(TipoAutomacao... tipos) {
                this.satisfaz = List.of(tipos);
                return this;
            }

            public Builder executor(Executor e) {
                this.executor = e;
                return this;
            }

            public Builder preparador(Preparador p) {
                this.preparador = p;
                return this;
            }

            public Builder saidasDinamicas(String origem) {
                this.saidasDinamicas = origem;
                return this;
            }

            public Builder ia() {
                this.ia = true;
                return this;
            }

            public Builder palavras(String... lista) {
                this.palavrasChave = List.of(lista);
                return this;
            }

            public Builder resumo(Function<Map<String, Object>, String> r) {
                this.resumo = r;
                return this;
            }

            public Builder evento(String chave) {
                this.chaveEvento = chave;
                return this;
            }

            public Builder subgrupo(String nome) {
                this.subgrupo = nome;
                return this;
            }

            public EspecNo criar() {
                return new EspecNo(tipo, grupo, rotulo, descricao, icone, params, saidas, conteiner, gatilho, efeitoExterno,
                        repeticaoPadrao, satisfaz, executor, preparador, saidasDinamicas, ia, palavrasChave, resumo,
                        chaveEvento, subgrupo);
            }
        }
    }

    /** Fonte de nós; cada grupo de nós é um bean. */
    public interface ProvedorNos {
        Collection<EspecNo> nos();
    }

    private final Map<String, EspecNo> nos = new LinkedHashMap<>();
    private final List<ProvedorNos> provedores;

    public Catalogo(List<ProvedorNos> provedores) {
        this.provedores = provedores;
    }

    private synchronized Map<String, EspecNo> registro() {
        if (nos.isEmpty()) {
            for (ProvedorNos provedor : provedores) {
                for (EspecNo no : provedor.nos()) {
                    if (nos.containsKey(no.tipo())) throw new IllegalStateException("Nó duplicado no catálogo: " + no.tipo());
                    nos.put(no.tipo(), no);
                }
            }
        }
        return nos;
    }

    public EspecNo buscar(String tipo) {
        return registro().get(tipo);
    }

    public Collection<EspecNo> todos() {
        return registro().values();
    }

    /** Gatilhos que reagem a um evento de domínio (chave do catálogo de gatilhos). */
    public List<EspecNo> gatilhosDoEvento(String chaveEvento) {
        return registro().values().stream().filter(n -> n.gatilho() && chaveEvento.equals(n.chaveEvento())).toList();
    }
}
