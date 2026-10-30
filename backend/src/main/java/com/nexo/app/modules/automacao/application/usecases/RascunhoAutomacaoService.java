package com.nexo.app.modules.automacao.application.usecases;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.automacao.application.dto.AutomacaoRequest;
import com.nexo.app.modules.automacao.application.dto.RascunhoResponse;
import com.nexo.app.modules.automacao.application.motor.Catalogo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Verificador;
import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.OrigemAutomacao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Funcoes;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.VersaoAutomacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;

/**
 * Monta uma automação a partir de um pedido em português (no chat do Copiloto ou no
 * "Pedir ao Copiloto" do designer).
 *
 * <p>O modelo recebe o catálogo de nós e devolve uma definição v3. Ela passa pela mesma
 * validação estrutural do designer e é salva como <b>rascunho</b> (ou como nova versão do
 * rascunho em ajuste). Nada roda até uma pessoa ativar.</p>
 */
@Service
public class RascunhoAutomacaoService {

    private static final Pattern JSON = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final List<String> CONTEINERES = List.of("then", "else", "steps", "default");

    private static final String SISTEMA = """
            Você monta automações (workflows, no estilo do Power Automate) para o Nexo — sistema de gestão da empresa,
            empresa de serviços de energia (descontos em folha, chamados, financeiro, almoxarifado, SST, frota, execução
            de equipes em campo). Responda SOMENTE com JSON:
            {"name": "nome curto", "kind": "EMAIL|ALERTA|ROTINA|APROVACAO|DADOS_IA|GERAL",
             "summary": "uma frase do que a automação faz",
             "missing": ["o que a pessoa ainda precisa informar, ex.: quem é o RH de Juazeiro"],
             "definition": {"schema": 3, "trigger": {"type": "...", "params": {...}},
               "variables": [{"name": "limite", "type": "number", "value": 100}], "steps": [nó, ...]}}

            Nó: {"id": "id_curto_sem_espaco", "type": "...", "label": "Rótulo em português", "params": {...}}
            Blocos (contêineres):
            - control.condition: "params": {"condition": {"op": "and|or", "rules": [{"left": "{{ ... }}", "operator": "eq", "right": "Juazeiro"}]}}, "then": [nós], "else": [nós]
            - control.switch: "params": {"on": "{{ ... }}"}, "cases": [{"id": "c1", "value": "Juazeiro", "steps": [nós]}], "default": [nós]
            - control.foreach: "params": {"items": "{{ lista }}"}, "steps": [nós]  (dentro use {{ item.campo }})
            - control.scope: "steps": [nós]; control.parallel: "branches": [{"id": "b1", "label": "...", "steps": [nós]}]
            Tratamento de erro: "run_after": ["failed"] faz o nó rodar só se o anterior falhar.

            Destinatários (parâmetros do tipo recipients/routes) são listas de {"tipo": "...", "valor": "...", "rotulo": "..."}:
            tipo usuario (valor = id do usuário), colaborador (id do colaborador), email (endereço), permissao (nome da
            permissão, ex.: desconto:baixar), perfil (id do perfil), papel (papel do evento, ex.: solicitante) ou
            expressao (valor = "{{ steps.x.outputs.recipients }}").
            Para "quem recebe depende da base/regional", use people.route: params {"value": "{{ trigger.outputs.colaborador.base }}",
            "routes": [{"when": "Juazeiro", "recipients": [...]}, ...], "fallback": [...]} e depois, no e-mail/aviso,
            "to"/"recipients": [{"tipo": "expressao", "valor": "{{ steps.<id>.outputs.recipients }}"}].
            Nunca invente ids nem e-mails: use só os escritos no pedido; senão deixe a lista vazia e cite em "missing".

            Expressões entre {{ }}: trigger.outputs.<campo>, steps.<id>.outputs.<campo>, vars.<nome>, item.<campo>,
            funções: %s. Um valor que é só {{ expr }} mantém o tipo (lista, número).

            E-mail (email.send): "body" em HTML simples (p, h2, h3, strong, em, ul, li, a). Dados no corpo:
            <span data-expr="steps.x.outputs.campo"></span>; blocos: <div data-block="markdown|table|list|html" data-source="steps.x.outputs.campo"></div>.
            Para relatórios com IA, use ai.copiloto (ele consulta os dados sozinho, com gráficos e PDF) e, no e-mail,
            <div data-block="markdown" data-source="steps.<id>.outputs.text"></div> e "attachments": "{{ steps.<id>.outputs.files }}".
            Para agenda semanal use trigger.schedule com {"frequency": "week", "weekdays": [0], "time": "07:00"} (0 = segunda … 6 = domingo).

            Regras:
            - Use só os tipos de nó e gatilho do catálogo enviado e os parâmetros listados.
            - Um nó só pode usar saídas de nós que rodam ANTES dele.
            - Nunca escreva números ou listas de dados à mão: use expressões, consultas e blocos.
            - IA interpreta, não decide: se uma condição usar resposta de IA antes de enviar algo para fora, inclua approval.request antes.
            - Ids em minúsculas, únicos, com _ (ex.: rotear_rh, enviar_email).
            """;

    private final CopilotoApi copiloto;
    private final Catalogo catalogo;
    private final Verificador verificador;
    private final AutomacaoUseCases useCases;
    private final AutomacaoRepository automacoes;
    private final VersaoAutomacaoRepository versoes;

    public RascunhoAutomacaoService(CopilotoApi copiloto, Catalogo catalogo, Verificador verificador,
                                    AutomacaoUseCases useCases, AutomacaoRepository automacoes, VersaoAutomacaoRepository versoes) {
        this.copiloto = copiloto;
        this.catalogo = catalogo;
        this.verificador = verificador;
        this.useCases = useCases;
        this.automacoes = automacoes;
        this.versoes = versoes;
    }

    // ------------------------------------------------------------------------------ catálogo para o prompt

    private Map<String, Object> catalogoParaPrompt() {
        List<Object> gatilhos = new ArrayList<>();
        List<Object> nos = new ArrayList<>();
        for (EspecNo no : catalogo.todos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", no.tipo());
            m.put("label", no.rotulo());
            m.put("description", no.descricao());
            if (no.conteiner() != null) m.put("container", no.conteiner());
            List<Object> params = new ArrayList<>();
            for (EspecParam p : no.params()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("key", p.chave());
                pm.put("kind", p.tipo());
                if (p.obrigatorio()) pm.put("required", true);
                if (!p.opcoes().isEmpty()) {
                    Map<String, String> opcoes = new LinkedHashMap<>();
                    for (List<String> o : p.opcoes()) opcoes.put(o.get(0), o.get(1));
                    pm.put("options", opcoes);
                }
                if (!p.campos().isEmpty()) pm.put("fields", p.campos().stream().map(Catalogo.EspecCampo::chave).toList());
                if (p.padrao() != null && !Valores.vazio(p.padrao())) pm.put("default", p.padrao());
                params.add(pm);
            }
            if (!params.isEmpty()) m.put("params", params);
            List<String> saidas = new ArrayList<>();
            for (EspecSaida s : no.saidas()) {
                saidas.add(s.campos().isEmpty() ? s.chave()
                        : s.chave() + (s.tipo().equals("array") ? "[]" : "") + ": " + String.join(", ", s.campos().stream().map(EspecSaida::chave).toList()));
            }
            if (!saidas.isEmpty()) m.put("outputs", saidas);
            (no.gatilho() ? gatilhos : nos).add(m);
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("triggers", gatilhos);
        c.put("nodes", nos);
        return c;
    }

    // ------------------------------------------------------------------------------ normalização

    private static List<Object> comoLista(Object valor) {
        if (valor == null || "".equals(valor)) return new ArrayList<>();
        if (valor instanceof List<?>) return Valores.lista(valor);
        return new ArrayList<>(List.of(valor));
    }

    private List<Object> normalizarNos(Object bruto, Set<String> usados) {
        List<Object> nos = new ArrayList<>();
        for (Object item : comoLista(bruto)) {
            if (!(item instanceof Map<?, ?> m) || Valores.vazio(Valores.mapa(m).get("type"))) continue;
            Map<String, Object> no = Valores.mapa(m);
            String tipo = Valores.texto(no.get("type"));
            String base = Valores.texto(no.get("id")).isBlank() ? (Valores.texto(no.get("label")).isBlank() ? tipo.substring(tipo.lastIndexOf('.') + 1) : Valores.texto(no.get("label"))) : Valores.texto(no.get("id"));
            no.put("id", DefinicaoAutomacao.idUnico(base, usados));
            Map<String, Object> params = Valores.mapa(no.get("params"));
            if (params.get("condition") instanceof Map<?, ?> cond && !cond.containsKey("rules")) {
                params.put("condition", Map.of("op", "and", "rules", List.of(cond)));
            }
            for (String chave : List.of("to", "cc", "recipients", "approvers", "fallback")) {
                if (params.containsKey(chave)) params.put(chave, destinatarios(params.get(chave)));
            }
            if (params.get("routes") instanceof List<?> rotas) {
                List<Object> normalizadas = new ArrayList<>();
                for (Object r : rotas) {
                    Map<String, Object> rota = Valores.mapa(r);
                    rota.put("recipients", destinatarios(rota.get("recipients")));
                    normalizadas.add(rota);
                }
                params.put("routes", normalizadas);
            }
            no.put("params", params);
            if (no.containsKey("run_after")) {
                List<Object> apos = comoLista(no.get("run_after"));
                no.put("run_after", apos.isEmpty() ? List.of("succeeded") : apos);
            }
            for (String chave : CONTEINERES) if (no.containsKey(chave)) no.put(chave, normalizarNos(no.get(chave), usados));
            if (no.get("cases") instanceof List<?> casos) {
                List<Object> lista = new ArrayList<>();
                for (Object c : casos) {
                    Map<String, Object> caso = Valores.mapa(c);
                    caso.put("id", DefinicaoAutomacao.idUnico(Valores.texto(caso.getOrDefault("id", "caso")), usados));
                    caso.put("value", Valores.texto(caso.get("value")));
                    caso.put("steps", normalizarNos(caso.get("steps"), usados));
                    lista.add(caso);
                }
                no.put("cases", lista);
            }
            if (no.get("branches") instanceof List<?> ramos) {
                List<Object> lista = new ArrayList<>();
                for (Object b : ramos) {
                    Map<String, Object> ramo = Valores.mapa(b);
                    ramo.put("id", DefinicaoAutomacao.idUnico(Valores.texto(ramo.getOrDefault("id", "ramo")), usados));
                    ramo.put("steps", normalizarNos(ramo.get("steps"), usados));
                    lista.add(ramo);
                }
                no.put("branches", lista);
            }
            nos.add(no);
        }
        return nos;
    }

    /** Aceita e-mails soltos e textos: tudo vira {tipo, valor}. */
    private static List<Object> destinatarios(Object bruto) {
        List<Object> lista = new ArrayList<>();
        if (bruto instanceof String s && s.contains("{{")) {
            lista.add(Map.of("tipo", "expressao", "valor", s));
            return lista;
        }
        for (Object item : comoLista(bruto instanceof String s ? List.of(s.split("[,;]")) : bruto)) {
            if (item instanceof Map<?, ?> m) {
                lista.add(Valores.mapa(m));
            } else {
                String t = Valores.texto(item).strip();
                if (t.isEmpty()) continue;
                if (t.contains("{{")) lista.add(Map.of("tipo", "expressao", "valor", t));
                else if (t.contains("@")) lista.add(Map.of("tipo", "email", "valor", t));
                else if (t.contains(":")) lista.add(Map.of("tipo", "permissao", "valor", t));
            }
        }
        return lista;
    }

    Map<String, Object> normalizar(Object bruto) {
        if (!(bruto instanceof Map<?, ?> m)) throw new IllegalArgumentException("definição ausente");
        Map<String, Object> d = Valores.mapa(m);
        d.put("schema", 3);
        Object gatilho = d.get("trigger");
        Map<String, Object> g = gatilho instanceof String s ? new LinkedHashMap<>(Map.of("type", s)) : Valores.mapa(gatilho);
        if (Valores.vazio(g.get("type"))) g.put("type", "trigger.manual");
        g.put("params", Valores.mapa(g.get("params")));
        d.put("trigger", g);
        d.put("steps", normalizarNos(d.get("steps"), new HashSet<>()));
        List<Object> variaveis = new ArrayList<>();
        for (Object v : comoLista(d.get("variables"))) if (v instanceof Map<?, ?> vm && !Valores.vazio(Valores.mapa(vm).get("name"))) variaveis.add(vm);
        d.put("variables", variaveis);
        return d;
    }

    private static Map<String, Object> lerJson(String texto) {
        Matcher m = JSON.matcher(texto);
        if (!m.find()) throw new InvarianteVioladaException("O Copiloto não devolveu uma automação. Tente explicar de outro jeito.");
        try {
            return Valores.mapa(Valores.json().readValue(m.group(), Object.class));
        } catch (JsonProcessingException e) {
            throw new InvarianteVioladaException("O Copiloto devolveu uma automação incompleta. Tente de novo.");
        }
    }

    // ------------------------------------------------------------------------------ rascunho

    @Transactional
    public RascunhoResponse rascunhar(String pedido, UUID automacaoId, UUID usuarioId) {
        if (!copiloto.disponivel()) throw new InvarianteVioladaException("O Copiloto está indisponível: o provedor de IA não está configurado.");
        Automacao existente = automacaoId == null ? null : automacoes.findById(automacaoId).filter(Automacao::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Automação não encontrada."));
        StringBuilder usuario = new StringBuilder();
        usuario.append("CATÁLOGO:\n").append(Valores.paraJson(catalogoParaPrompt())).append("\n\n");
        if (existente != null) {
            Map<String, Object> atual = versoes.findByIdAutomacaoAndNumero(existente.getId(), existente.getNumeroVersao())
                    .map(v -> v.getDefinicao()).orElse(AutomacaoUseCases.branca());
            usuario.append("AUTOMAÇÃO ATUAL (ajuste-a; mantenha o que não foi pedido para mudar, inclusive os ids):\n")
                    .append(Valores.paraJson(Map.of("name", existente.getNome(), "kind", existente.getTipo().name(), "definition", atual)))
                    .append("\n\n");
        }
        usuario.append("PEDIDO:\n").append(pedido.strip());
        String resposta;
        try {
            resposta = copiloto.completar(SISTEMA.formatted(String.join(", ", Funcoes.TODAS.keySet())), usuario.toString(), 0.1, 150);
        } catch (RuntimeException e) {
            throw new InvarianteVioladaException("O Copiloto não respondeu agora. Tente de novo em instantes.");
        }
        Map<String, Object> json = lerJson(resposta);
        Map<String, Object> definicaoBruta;
        try {
            definicaoBruta = normalizar(json.get("definition"));
        } catch (IllegalArgumentException e) {
            throw new InvarianteVioladaException("O Copiloto não montou os passos. Detalhe mais o pedido.");
        }
        if (existente != null && existente.getId() != null) {
            Map<String, Object> layoutAtual = versoes.findByIdAutomacaoAndNumero(existente.getId(), existente.getNumeroVersao())
                    .map(v -> Valores.mapa(v.getDefinicao().get("layout"))).orElse(Map.of());
            definicaoBruta.putIfAbsent("layout", layoutAtual);
        }
        DefinicaoAutomacao definicao;
        try {
            definicao = DefinicaoAutomacao.ler(definicaoBruta);
        } catch (InvarianteVioladaException e) {
            throw new InvarianteVioladaException("A automação sugerida tem um problema de estrutura: " + e.getMessage());
        }
        // Tipos fora do catálogo são descartados em vez de recusar o rascunho inteiro.
        definicaoBruta.put("steps", podar(Valores.lista(definicaoBruta.get("steps"))));
        definicao = DefinicaoAutomacao.ler(definicaoBruta);
        if (catalogo.buscar(definicao.gatilho().tipo()) == null) {
            definicaoBruta.put("trigger", Map.of("type", "trigger.manual", "params", Map.of()));
            definicao = DefinicaoAutomacao.ler(definicaoBruta);
        }
        TipoAutomacao tipo;
        try {
            tipo = TipoAutomacao.valueOf(Valores.texto(json.get("kind")).strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            tipo = verificador.sugerirTipo(definicao);
        }
        String nome = Valores.texto(json.get("name")).strip();
        if (nome.isEmpty()) nome = existente != null ? existente.getNome() : "Nova automação";
        if (nome.length() > 120) nome = nome.substring(0, 120);
        String resumo = Valores.texto(json.get("summary")).strip();
        List<String> faltando = new ArrayList<>();
        for (Object f : comoLista(json.get("missing"))) if (!Valores.texto(f).isBlank()) faltando.add(Valores.texto(f).strip());
        UUID id;
        if (existente == null) {
            Automacao nova = useCases.criarInterno(new AutomacaoRequest(nome, resumo.length() > 500 ? resumo.substring(0, 500) : resumo,
                    tipo, definicao.paraMapa(), "Rascunho do Copiloto: " + cortar(pedido, 250)), OrigemAutomacao.COPILOTO, usuarioId, null);
            id = nova.getId();
        } else {
            useCases.salvar(existente.getId(), new AutomacaoRequest(nome, existente.getDescricao() == null ? resumo : existente.getDescricao(),
                    tipo, definicao.paraMapa(), "Ajuste do Copiloto: " + cortar(pedido, 250)));
            id = existente.getId();
        }
        List<String> problemas = verificador.problemasDeAtivacao(definicao, tipo);
        return new RascunhoResponse(id, nome, tipo, resumo, faltando, definicao.paraMapa(), problemas);
    }

    private List<Object> podar(List<Object> nos) {
        List<Object> resultado = new ArrayList<>();
        for (Object item : nos) {
            Map<String, Object> no = Valores.mapa(item);
            if (catalogo.buscar(Valores.texto(no.get("type"))) == null) continue;
            for (String chave : CONTEINERES) if (no.get(chave) instanceof List<?> l) no.put(chave, podar(Valores.lista(l)));
            if (no.get("cases") instanceof List<?> casos) {
                List<Object> lista = new ArrayList<>();
                for (Object c : casos) {
                    Map<String, Object> caso = Valores.mapa(c);
                    caso.put("steps", podar(Valores.lista(caso.get("steps"))));
                    lista.add(caso);
                }
                no.put("cases", lista);
            }
            if (no.get("branches") instanceof List<?> ramos) {
                List<Object> lista = new ArrayList<>();
                for (Object r : ramos) {
                    Map<String, Object> ramo = Valores.mapa(r);
                    ramo.put("steps", podar(Valores.lista(ramo.get("steps"))));
                    lista.add(ramo);
                }
                no.put("branches", lista);
            }
            resultado.add(no);
        }
        return resultado;
    }

    private static String cortar(String texto, int limite) {
        String t = texto == null ? "" : texto.strip();
        return t.length() > limite ? t.substring(0, limite) + "…" : t;
    }
}
