package com.nexo.app.modules.automacao.application.motor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.No;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.Variavel;
import com.nexo.app.modules.automacao.domain.expressao.Condicoes;
import com.nexo.app.modules.automacao.domain.expressao.ErroExpressao;
import com.nexo.app.modules.automacao.domain.expressao.Expressoes;
import com.nexo.app.modules.automacao.domain.expressao.Expressoes.Referencia;
import com.nexo.app.modules.automacao.domain.expressao.Valores;

/**
 * O verificador do fluxo: erros (bloqueiam a ativação) e avisos, por nó. O designer
 * chama a mesma verificação enquanto a pessoa edita, para o retorno ser imediato; o
 * resultado do servidor é o que vale.
 */
@Component
public class Verificador {

    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Parâmetros avaliados por item ({{ item }} é o elemento da lista ali). */
    public static final Set<String> PARAMS_POR_ITEM = Set.of("data.filter:condition", "data.select:mapping", "data.table:columns");
    private static final Set<String> CAMPOS_PASSO = Set.of("outputs", "status", "error");
    private static final Pattern DADO_HTML = Pattern.compile("data-(?:expr|source)=\"([^\"]*)\"");

    public record Problema(String severidade, String mensagem, String noId, String param) {
    }

    private record Visibilidade(Set<String> passos, List<String> loops) {
    }

    private final Catalogo catalogo;

    public Verificador(Catalogo catalogo) {
        this.catalogo = catalogo;
    }

    public List<Problema> verificar(DefinicaoAutomacao definicao) {
        return new Execucao(definicao).rodar();
    }

    public List<String> problemasDeAtivacao(DefinicaoAutomacao definicao, TipoAutomacao tipo) {
        List<String> erros = new ArrayList<>();
        for (Problema p : verificar(definicao)) {
            if (p.severidade().equals("error")) {
                erros.add((p.noId() != null && !p.noId().equals("trigger") ? p.noId() + ": " : "") + p.mensagem());
            }
        }
        erros.addAll(problemasDoTipo(definicao, tipo));
        return erros;
    }

    public List<String> problemasDoTipo(DefinicaoAutomacao definicao, TipoAutomacao tipo) {
        if (tipo == TipoAutomacao.ROTINA) {
            return definicao.gatilho().tipo().equals("trigger.manual")
                    ? List.of("Uma rotina roda sozinha: use agenda, limiar ou um gatilho de evento") : List.of();
        }
        String mensagem = switch (tipo) {
            case EMAIL -> "A automação de e-mail não envia nenhum e-mail";
            case ALERTA -> "O alerta não avisa ninguém: adicione 'Avisar pessoas' ou 'Enviar e-mail'";
            case APROVACAO -> "A automação de aprovação não tem 'Pedir aprovação'";
            case DADOS_IA -> "A automação de dados e IA não tem passo de dados ou de IA";
            default -> null;
        };
        if (mensagem == null) return List.of();
        boolean atende = false;
        for (No no : definicao.todosOsNos()) {
            EspecNo spec = catalogo.buscar(no.tipo());
            if (spec != null && spec.satisfaz().contains(tipo)) atende = true;
            if (tipo == TipoAutomacao.DADOS_IA && (no.tipo().startsWith("data.") || no.tipo().startsWith("ai."))) atende = true;
        }
        return atende ? List.of() : List.of(mensagem);
    }

    public TipoAutomacao sugerirTipo(DefinicaoAutomacao definicao) {
        Set<String> tipos = new HashSet<>();
        for (No no : definicao.todosOsNos()) tipos.add(no.tipo());
        if (tipos.contains("approval.request")) return TipoAutomacao.APROVACAO;
        if (tipos.contains("ai.copiloto") || tipos.stream().anyMatch(t -> t.startsWith("ai."))) {
            return definicao.gatilho().tipo().equals("trigger.schedule") ? TipoAutomacao.ROTINA : TipoAutomacao.DADOS_IA;
        }
        if (tipos.contains("email.send")) return TipoAutomacao.EMAIL;
        if (tipos.contains("notify.people")) return TipoAutomacao.ALERTA;
        if (!definicao.gatilho().tipo().equals("trigger.manual")) return TipoAutomacao.ROTINA;
        return TipoAutomacao.GERAL;
    }

    private final class Execucao {
        private final DefinicaoAutomacao definicao;
        private final List<Problema> problemas = new ArrayList<>();
        private final Set<String> variaveis = new HashSet<>();
        private final Map<String, EspecNo> specs = new HashMap<>();
        private final Set<String> nosIa = new HashSet<>();

        Execucao(DefinicaoAutomacao definicao) {
            this.definicao = definicao;
            for (Variavel v : definicao.variaveis()) variaveis.add(v.nome());
            for (No no : definicao.todosOsNos()) {
                EspecNo spec = catalogo.buscar(no.tipo());
                if (spec != null) {
                    specs.put(no.id(), spec);
                    if (spec.ia()) nosIa.add(no.id());
                }
            }
        }

        void add(String severidade, String mensagem, String noId, String param) {
            problemas.add(new Problema(severidade, mensagem, noId, param));
        }

        Set<String> chavesSaida(EspecNo spec) {
            if (spec.saidasDinamicas() != null) return null;
            Set<String> chaves = new HashSet<>();
            for (EspecSaida s : spec.saidas()) chaves.add(s.chave());
            if (spec.ia()) chaves.add("gerado_por_ia");
            return chaves;
        }

        Set<String> verificarValor(String noId, String param, Object valor, Visibilidade visivel, boolean itemPermitido) {
            List<Referencia> referencias;
            try {
                referencias = Expressoes.referencias(valor);
            } catch (ErroExpressao e) {
                add("error", "Expressão inválida: " + e.getMessage(), noId, param);
                return Set.of();
            }
            Set<String> lidos = new HashSet<>();
            for (Referencia ref : referencias) {
                switch (ref.raiz()) {
                    case "steps" -> {
                        if (ref.nome() == null) {
                            add("error", "Indique o passo: steps.<id>.outputs…", noId, param);
                            continue;
                        }
                        lidos.add(ref.nome());
                        if (ref.nome().equals(noId)) {
                            add("error", "Um passo não pode usar a própria saída", noId, param);
                        } else if (!specs.containsKey(ref.nome())) {
                            add("error", "O passo '" + ref.nome() + "' não existe", noId, param);
                        } else if (!visivel.passos().contains(ref.nome())) {
                            add("error", "O passo '" + ref.nome() + "' não roda antes deste (ou está dentro de um 'Para cada' ou de outro ramo)", noId, param);
                        } else if (!ref.resto().isEmpty() && !CAMPOS_PASSO.contains(ref.resto().get(0))) {
                            add("warning", "Use steps." + ref.nome() + ".outputs." + ref.resto().get(0), noId, param);
                        } else if (ref.resto().size() >= 2 && ref.resto().get(0).equals("outputs")) {
                            Set<String> chaves = chavesSaida(specs.get(ref.nome()));
                            if (chaves != null && !chaves.contains(ref.resto().get(1))) {
                                add("warning", "O passo '" + ref.nome() + "' não tem a saída '" + ref.resto().get(1) + "'", noId, param);
                            }
                        }
                    }
                    case "vars" -> {
                        if (ref.nome() != null && !variaveis.contains(ref.nome())) {
                            add("error", "A variável '" + ref.nome() + "' não foi declarada", noId, param);
                        }
                    }
                    case "item" -> {
                        if (!itemPermitido && visivel.loops().isEmpty()) {
                            add("error", "{{ item }} só existe dentro de 'Para cada' (ou nas regras de 'Filtrar lista')", noId, param);
                        }
                    }
                    case "loop" -> {
                        if (ref.nome() != null && !visivel.loops().contains(ref.nome())) {
                            add("error", "'" + ref.nome() + "' não é um 'Para cada' que contém este passo", noId, param);
                        }
                    }
                    case "trigger" -> {
                        if (ref.nome() != null && !ref.nome().equals("outputs") && !ref.nome().equals("type")) {
                            add("warning", "Use trigger.outputs.<campo>", noId, param);
                        } else if ("outputs".equals(ref.nome()) && !ref.resto().isEmpty()) {
                            EspecNo spec = catalogo.buscar(definicao.gatilho().tipo());
                            if (spec != null) {
                                Set<String> chaves = chavesSaida(spec);
                                if (chaves != null && !chaves.contains(ref.resto().get(0))) {
                                    add("warning", "O gatilho não tem a saída '" + ref.resto().get(0) + "'", noId, param);
                                }
                            }
                        }
                    }
                    default -> {
                        // run, automation, env: sempre disponíveis
                    }
                }
            }
            return lidos;
        }

        Set<String> verificarCondicao(String noId, String param, Object bruto, Visibilidade visivel, boolean itemPermitido) {
            Condicoes.Grupo grupo;
            try {
                grupo = Condicoes.ler(bruto);
            } catch (ErroExpressao e) {
                add("error", "Regras inválidas: " + e.getMessage(), noId, param);
                return Set.of();
            }
            List<Condicoes.Regra> regras = Condicoes.regras(grupo);
            Set<String> lidos = new HashSet<>();
            if (regras.isEmpty()) add("warning", "Sem regras: será sempre verdadeiro", noId, param);
            if (regras.size() > Condicoes.MAX_REGRAS) add("error", "No máximo " + Condicoes.MAX_REGRAS + " regras", noId, param);
            for (Condicoes.Regra regra : regras) {
                if (Valores.vazio(regra.esquerda())) add("error", "Regra sem valor à esquerda", noId, param);
                lidos.addAll(verificarValor(noId, param, regra.esquerda(), visivel, itemPermitido));
                lidos.addAll(verificarValor(noId, param, regra.direita(), visivel, itemPermitido));
            }
            return lidos;
        }

        Set<String> verificarParams(No no, String noId, EspecNo spec, Map<String, Object> params, Visibilidade visivel) {
            Set<String> lidos = new HashSet<>();
            for (EspecParam param : spec.params()) {
                if (param.mostrarSe() != null && !param.mostrarSe().isEmpty()) {
                    String outro = param.mostrarSe().get(0);
                    EspecParam outroSpec = spec.param(outro);
                    Object atual = params.containsKey(outro) ? params.get(outro) : (outroSpec == null ? null : outroSpec.padrao());
                    if (!param.mostrarSe().subList(1, param.mostrarSe().size()).contains(Valores.texto(atual))) continue;
                }
                Object valor = params.containsKey(param.chave()) ? params.get(param.chave()) : param.padrao();
                if (param.obrigatorio() && Valores.vazio(valor) && !param.tipo().equals("condition")) {
                    add("error", "Preencha '" + param.rotulo() + "'", noId, param.chave());
                    continue;
                }
                boolean itemPermitido = PARAMS_POR_ITEM.contains(spec.tipo() + ":" + param.chave());
                if (param.tipo().equals("condition")) {
                    lidos.addAll(verificarCondicao(noId == null ? "trigger" : noId, param.chave(), valor, visivel, itemPermitido));
                    continue;
                }
                if (param.tipo().equals("emails")) {
                    for (Object item : Valores.lista(valor)) {
                        String texto = Valores.texto(item).strip();
                        if (!texto.isEmpty() && !texto.contains("{{") && !EMAIL.matcher(texto.toLowerCase()).matches()) {
                            add("error", "E-mail inválido: " + texto, noId, param.chave());
                        }
                    }
                }
                if (param.tipo().equals("recipients") || param.tipo().equals("routes")) {
                    verificarDestinatarios(noId, param, valor);
                }
                if (param.tipo().equals("select") && !param.dinamico() && !param.opcoes().isEmpty() && !Valores.vazio(valor)) {
                    boolean valida = param.opcoes().stream().anyMatch(o -> o.get(0).equals(Valores.texto(valor)));
                    if (!valida) add("error", "Opção inválida em '" + param.rotulo() + "'", noId, param.chave());
                }
                if (param.dinamico() || List.of("mapping", "columns", "expression", "recipients", "routes").contains(param.tipo())) {
                    lidos.addAll(verificarValor(noId, param.chave(), valor, visivel, itemPermitido));
                }
                if (param.tipo().equals("html") && valor instanceof String html) {
                    Matcher m = DADO_HTML.matcher(html);
                    while (m.find()) {
                        String expr = m.group(1).replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
                        if (!expr.isBlank()) lidos.addAll(verificarValor(noId, param.chave(), "{{" + expr + "}}", visivel, false));
                    }
                    lidos.addAll(verificarValor(noId, param.chave(), html.replaceAll("<[^>]+>", " "), visivel, false));
                }
            }
            if (no != null && no.tipo().startsWith("variable.")) {
                String nome = Valores.texto(params.get("name"));
                if (!nome.isEmpty() && !variaveis.contains(nome)) {
                    add("error", "A variável '" + nome + "' não foi declarada", noId, "name");
                }
            }
            return lidos;
        }

        void verificarDestinatarios(String noId, EspecParam param, Object valor) {
            List<Object> entradas = new ArrayList<>();
            if (param.tipo().equals("routes")) {
                for (Object rota : Valores.lista(valor)) entradas.addAll(Valores.lista(Valores.mapa(rota).get("recipients")));
            } else {
                entradas.addAll(Valores.lista(valor));
            }
            for (Object entrada : entradas) {
                Map<String, Object> d = Valores.mapa(entrada);
                String tipo = Valores.texto(d.get("tipo"));
                String alvo = Valores.texto(d.get("valor")).strip();
                if (alvo.isEmpty()) {
                    add("error", "Há um destinatário sem valor em '" + param.rotulo() + "'", noId, param.chave());
                } else if (tipo.equals("email") && !alvo.contains("{{") && !EMAIL.matcher(alvo.toLowerCase()).matches()) {
                    add("error", "E-mail inválido: " + alvo, noId, param.chave());
                }
            }
        }

        Set<String> percorrer(List<No> nos, Visibilidade visivel) {
            Set<String> produzidos = new LinkedHashSet<>();
            for (int posicao = 0; posicao < nos.size(); posicao++) {
                No no = nos.get(posicao);
                Set<String> passos = new HashSet<>(visivel.passos());
                passos.addAll(produzidos);
                Visibilidade atual = new Visibilidade(passos, visivel.loops());
                EspecNo spec = catalogo.buscar(no.tipo());
                if (spec == null) {
                    add("error", "Tipo de passo desconhecido: " + no.tipo(), no.id(), null);
                    produzidos.add(no.id());
                    continue;
                }
                if (spec.gatilho()) {
                    add("error", "Um gatilho só pode ficar no topo", no.id(), null);
                    continue;
                }
                if (posicao == 0 && !no.executarApos().contains("succeeded") && visivel.loops().isEmpty() && produzidos.isEmpty()) {
                    add("warning", "Primeiro passo de um bloco: com 'Executar após' sem 'sucesso' ele nunca roda", no.id(), null);
                }
                verificarSlots(no, spec);
                Set<String> lidos = verificarParams(no, no.id(), spec, no.params(), atual);
                avisoDeIa(no, spec, lidos);
                produzidos.add(no.id());
                if (spec.conteiner() == null) continue;
                for (Map.Entry<String, List<No>> filho : no.filhos()) {
                    String slot = filho.getKey();
                    if (filho.getValue().isEmpty() && !slot.equals("else") && !slot.equals("default")) {
                        add("warning", "Bloco vazio", no.id(), null);
                    }
                    if (spec.conteiner().equals("loop") || spec.conteiner().equals("until")) {
                        List<String> loops = new ArrayList<>(atual.loops());
                        if (spec.conteiner().equals("loop")) loops.add(no.id());
                        percorrer(filho.getValue(), new Visibilidade(atual.passos(), loops));
                    } else {
                        produzidos.addAll(percorrer(filho.getValue(), atual));
                    }
                }
            }
            return produzidos;
        }

        void verificarSlots(No no, EspecNo spec) {
            Set<String> permitidos = switch (spec.conteiner() == null ? "" : spec.conteiner()) {
                case "condition" -> Set.of("then", "else");
                case "switch" -> Set.of("case", "default");
                case "loop", "until", "scope" -> Set.of("steps");
                case "parallel" -> Set.of("branch");
                default -> Set.of();
            };
            for (Map.Entry<String, List<No>> filho : no.filhos()) {
                if (!permitidos.contains(filho.getKey().split(":")[0])) {
                    add("error", "'" + spec.rotulo() + "' não tem o bloco '" + filho.getKey() + "'", no.id(), null);
                }
            }
            if ("switch".equals(spec.conteiner())) {
                List<String> valores = new ArrayList<>();
                if (no.casos() != null) for (var c : no.casos()) valores.add(c.valor().strip().toLowerCase());
                if (valores.isEmpty()) add("warning", "Sem casos: tudo vai para o Padrão", no.id(), null);
                if (valores.size() != new HashSet<>(valores).size()) add("error", "Há casos com o mesmo valor", no.id(), null);
                if (valores.contains("")) add("warning", "Há um caso sem valor", no.id(), null);
            }
            if ("parallel".equals(spec.conteiner()) && (no.ramos() == null || no.ramos().size() < 2)) {
                add("warning", "Ramos paralelos precisam de ao menos dois ramos", no.id(), null);
            }
        }

        void avisoDeIa(No no, EspecNo spec, Set<String> lidos) {
            boolean decide = "condition".equals(spec.conteiner()) || "switch".equals(spec.conteiner()) || no.tipo().equals("data.filter");
            if (!decide || lidos.stream().noneMatch(nosIa::contains)) return;
            boolean efeito = false;
            boolean aprovacao = false;
            for (No n : definicao.todosOsNos()) {
                EspecNo s = catalogo.buscar(n.tipo());
                if (s != null && s.efeitoExterno()) efeito = true;
                if (n.tipo().equals("approval.request")) aprovacao = true;
            }
            if (efeito && !aprovacao) {
                add("warning", "Esta decisão usa uma resposta da IA. A IA interpreta, não decide: inclua 'Pedir aprovação' antes de enviar algo para fora.", no.id(), null);
            }
        }

        List<Problema> rodar() {
            EspecNo gatilho = catalogo.buscar(definicao.gatilho().tipo());
            if (gatilho == null || !gatilho.gatilho()) {
                add("error", "Gatilho desconhecido: " + definicao.gatilho().tipo(), "trigger", null);
            } else {
                verificarParams(null, "trigger", gatilho, definicao.gatilho().params(), new Visibilidade(Set.of(), List.of()));
                if (gatilho.tipo().equals("trigger.schedule")) {
                    try {
                        Recorrencia.verificar(definicao.gatilho().params());
                    } catch (IllegalArgumentException e) {
                        add("error", e.getMessage(), "trigger", null);
                    }
                }
            }
            for (Variavel v : definicao.variaveis()) {
                verificarValor(null, "var:" + v.nome(), v.valor(), new Visibilidade(Set.of(), List.of()), false);
            }
            if (definicao.passos().isEmpty()) add("error", "Adicione ao menos um passo depois do gatilho", null, null);
            percorrer(definicao.passos(), new Visibilidade(Set.of(), List.of()));
            for (String email : definicao.configuracoes().avisarFalha()) {
                if (!EMAIL.matcher(email.strip().toLowerCase()).matches()) {
                    add("error", "E-mail inválido em 'Avisar quando falhar': " + email, null, null);
                }
            }
            return problemas;
        }
    }
}
