package com.nexo.app.modules.automacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexo.app.modules.automacao.application.dto.AutomacaoRequest;
import com.nexo.app.modules.automacao.application.motor.Verificador;
import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.OrigemAutomacao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.GatilhoCatalogado;
import com.nexo.app.modules.notificacao.NotificacaoApi;
import com.nexo.app.modules.notificacao.RegraLegada;

/**
 * Converte as regras de notificação (e as políticas de roteamento que elas usam) em
 * automações equivalentes, uma única vez.
 *
 * <p>Regra por ocorrência vira gatilho de evento; regra por limiar vira gatilho de limiar
 * com o mesmo limiar e o mesmo silêncio. Condições viram um bloco "Condição" com as mesmas
 * regras (todas com "e"). Destinatários viram "Avisar pessoas" com os mesmos tipos; uma
 * política de roteamento vira "Escolher destinatários por regra" quando é uma tabela de
 * valores (ex.: a base decide), ou uma cadeia de condições quando é mais complexa. Os
 * textos ganham as expressões novas ({{ total }} → {{ trigger.outputs.total }}).</p>
 *
 * <p>Uma regra ativa só é desligada quando a automação que a substitui ativou sem
 * problemas; se algo impedir a ativação, a regra antiga continua avisando e a conversão é
 * tentada de novo na próxima inicialização.</p>
 */
@Component
public class ConversorRegrasLegadas {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversorRegrasLegadas.class);
    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Map<String, String> OPERADORES = Map.of("IGUAL", "eq", "DIFERENTE", "ne", "MAIOR_QUE", "gt",
            "MAIOR_OU_IGUAL", "gte", "MENOR_QUE", "lt", "MENOR_OU_IGUAL", "lte", "CONTEM", "contains", "EM", "in");

    private final NotificacaoApi notificacoes;
    private final AutomacaoRepository automacoes;
    private final AutomacaoUseCases useCases;
    private final Verificador verificador;
    private final UsuarioApi usuarios;
    private final TransactionTemplate transacao;

    public ConversorRegrasLegadas(NotificacaoApi notificacoes, AutomacaoRepository automacoes, AutomacaoUseCases useCases,
                                  Verificador verificador, UsuarioApi usuarios, TransactionTemplate transacao) {
        this.notificacoes = notificacoes;
        this.automacoes = automacoes;
        this.useCases = useCases;
        this.verificador = verificador;
        this.usuarios = usuarios;
        this.transacao = transacao;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void converterNaInicializacao() {
        try {
            int convertidas = converterTodas();
            if (convertidas > 0) LOGGER.info("{} regra(s) de notificação convertida(s) em automação.", convertidas);
        } catch (RuntimeException e) {
            LOGGER.error("Conversão das regras de notificação em automações falhou", e);
        }
    }

    public int converterTodas() {
        List<RegraLegada> regras = notificacoes.regrasNaoConvertidas();
        if (regras.isEmpty()) return 0;
        Optional<UUID> dono = responsavelPadrao();
        if (dono.isEmpty()) {
            LOGGER.warn("Nenhum usuário com 'automacao:manage' para ser responsável pelas regras convertidas; conversão adiada.");
            return 0;
        }
        Map<String, GatilhoCatalogado> gatilhos = new LinkedHashMap<>();
        for (GatilhoCatalogado g : notificacoes.catalogoGatilhos()) gatilhos.put(g.chave(), g);
        int total = 0;
        for (RegraLegada regra : regras) {
            GatilhoCatalogado gatilho = gatilhos.get(regra.chaveGatilho());
            if (gatilho == null) continue;
            try {
                Boolean ok = transacao.execute(s -> converter(regra, gatilho, dono.get()));
                if (Boolean.TRUE.equals(ok)) total++;
            } catch (RuntimeException e) {
                LOGGER.error("Regra '{}' não pôde ser convertida", regra.nome(), e);
            }
        }
        return total;
    }

    private Optional<UUID> responsavelPadrao() {
        for (String permissao : List.of("automacao:manage", "notificacao:regra:manage")) {
            List<UUID> ids = usuarios.buscarIdsAtivosComPermissao(permissao);
            if (!ids.isEmpty()) return Optional.of(ids.get(0));
        }
        return Optional.empty();
    }

    private boolean converter(RegraLegada regra, GatilhoCatalogado gatilho, UUID dono) {
        Automacao existente = automacoes.findByIdRegraOrigem(regra.id()).orElse(null);
        if (existente == null) {
            Map<String, Object> definicao = definicao(regra, gatilho);
            existente = useCases.criarInterno(new AutomacaoRequest(cortar(regra.nome(), 120),
                    regra.descricao() == null ? "Convertida da regra de notificação \"" + regra.nome() + "\"." : regra.descricao(),
                    TipoAutomacao.ALERTA, definicao, "Convertida da regra de notificação"), OrigemAutomacao.REGRA_CONVERTIDA, dono, regra.id());
        }
        DefinicaoAutomacao def = DefinicaoAutomacao.ler(useCases.versao(existente.getId(), existente.getNumeroVersao()));
        if (regra.ativa()) {
            List<String> problemas = verificador.problemasDeAtivacao(def, existente.getTipo());
            if (!problemas.isEmpty()) {
                LOGGER.warn("Regra ativa '{}' convertida, mas a automação não ativou ({}); a regra antiga segue valendo.",
                        regra.nome(), String.join("; ", problemas));
                return false;
            }
            existente.ativar(problemas, OffsetDateTime.now());
        } else if (existente.getStatus() == com.nexo.app.modules.automacao.domain.StatusAutomacao.RASCUNHO) {
            existente.pausar();
        }
        automacoes.save(existente);
        notificacoes.marcarRegraConvertida(regra.id(), existente.getId());
        return true;
    }

    // ------------------------------------------------------------------------------ definição

    /**
     * Avisos que, depois de convertidos, vão só para a base do colaborador: o desconto validado
     * chega ao RH da base dele, não ao de todas (evento|permissão).
     */
    static final Set<String> SO_DA_BASE = Set.of("desconto.validado|desconto:baixar");
    static final String BASE_DO_COLABORADOR = "{{ trigger.outputs.colaborador.base }}";

    Map<String, Object> definicao(RegraLegada regra, GatilhoCatalogado gatilho) {
        Set<String> ids = new HashSet<>();
        boolean limiar = "LIMIAR".equals(gatilho.modo());
        Map<String, Object> trigger = new LinkedHashMap<>();
        if (limiar) {
            trigger.put("type", "threshold." + gatilho.chave());
            trigger.put("params", new LinkedHashMap<>(Map.of("limiar", regra.limiar() == null ? 1 : regra.limiar(),
                    "silencio_minutos", regra.cooldownMinutos(), "verificar_minutos", 15)));
        } else {
            trigger.put("type", "event." + gatilho.chave());
            trigger.put("params", new LinkedHashMap<>());
        }

        List<Object> destinatariosFixos = new ArrayList<>();
        List<RegraLegada.Politica> politicas = new ArrayList<>();
        for (RegraLegada.Destinatario d : regra.destinatarios()) {
            switch (d.tipo()) {
                case "PERMISSAO" -> {
                    Map<String, Object> m = dest("permissao", d.valorAlvo(), d.valorAlvo());
                    if (SO_DA_BASE.contains(gatilho.chave() + "|" + d.valorAlvo())) m.put("base", BASE_DO_COLABORADOR);
                    destinatariosFixos.add(m);
                }
                case "PERFIL" -> destinatariosFixos.add(dest("perfil", String.valueOf(d.idAlvo()), null));
                case "USUARIO" -> destinatariosFixos.add(dest("usuario", String.valueOf(d.idAlvo()), null));
                case "PAPEL_DINAMICO" -> destinatariosFixos.add(dest("papel", d.valorAlvo(), d.valorAlvo()));
                case "POLITICA" -> {
                    if (d.politica() != null) politicas.add(d.politica());
                }
                default -> {
                    // tipo desconhecido: ignorado
                }
            }
        }

        List<Object> passos = new ArrayList<>();
        List<Object> recipients = new ArrayList<>(destinatariosFixos);
        List<Object> cadeiaPolitica = null;
        for (RegraLegada.Politica politica : politicas) {
            Map<String, Object> rota = rotaPorValor(politica, ids);
            if (rota != null) {
                passos.add(rota);
                recipients.add(dest("expressao", "{{ steps." + rota.get("id") + ".outputs.recipients }}", politica.nome()));
            } else if (cadeiaPolitica == null) {
                cadeiaPolitica = new ArrayList<>(List.of(politica));
            }
        }

        List<Object> acoes;
        if (cadeiaPolitica != null) {
            acoes = List.of(cadeia((RegraLegada.Politica) cadeiaPolitica.get(0), 0, regra, recipients, limiar, ids));
        } else {
            acoes = List.of(avisar(regra, recipients, limiar, ids, null));
        }

        if (!regra.condicoes().isEmpty()) {
            List<Object> regrasCondicao = new ArrayList<>();
            for (RegraLegada.Condicao c : regra.condicoes()) regrasCondicao.add(regraCondicao(c));
            Map<String, Object> condicao = no("control.condition", "Condições da regra", ids);
            condicao.put("params", new LinkedHashMap<>(Map.of("condition", Map.of("op", "and", "rules", regrasCondicao))));
            List<Object> entao = new ArrayList<>(passos);
            entao.addAll(acoes);
            condicao.put("then", entao);
            condicao.put("else", new ArrayList<>());
            passos = new ArrayList<>(List.of(condicao));
        } else {
            passos.addAll(acoes);
        }

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("schema", 3);
        d.put("trigger", trigger);
        d.put("variables", new ArrayList<>());
        d.put("steps", passos);
        d.put("settings", new LinkedHashMap<>(Map.of("notify_on_failure", new ArrayList<>(), "timeout_hours", 168)));
        d.put("layout", new LinkedHashMap<>());
        return d;
    }

    private static Map<String, Object> dest(String tipo, String valor, String rotulo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("valor", valor);
        if (rotulo != null) m.put("rotulo", rotulo);
        return m;
    }

    private static Map<String, Object> no(String tipo, String rotulo, Set<String> ids) {
        Map<String, Object> no = new LinkedHashMap<>();
        no.put("id", DefinicaoAutomacao.idUnico(rotulo, ids));
        no.put("type", tipo);
        no.put("label", rotulo);
        return no;
    }

    private static Map<String, Object> regraCondicao(RegraLegada.Condicao c) {
        String op = OPERADORES.getOrDefault(c.operador(), "eq");
        String direita = op.equals("in") ? c.valor().replace(',', ';') : c.valor();
        return Map.of("left", "{{ trigger.outputs." + c.campo() + " }}", "operator", op, "right", direita);
    }

    /** Política que é uma tabela de valores sobre um mesmo campo vira "Escolher destinatários por regra". */
    private Map<String, Object> rotaPorValor(RegraLegada.Politica politica, Set<String> ids) {
        String campo = null;
        List<Object> rotas = new ArrayList<>();
        List<Object> padrao = new ArrayList<>();
        for (RegraLegada.Rota r : politica.rotas()) {
            if (r.padrao()) {
                padrao.add(dest("usuario", r.usuarioId().toString(), null));
                continue;
            }
            if (r.condicoes().size() != 1) return null;
            RegraLegada.Condicao c = r.condicoes().get(0);
            if (!c.operador().equals("IGUAL") && !c.operador().equals("EM")) return null;
            if (campo != null && !campo.equals(c.campo())) return null;
            campo = c.campo();
            rotas.add(Map.of("when", c.valor().replace(',', ';'), "recipients", List.of(dest("usuario", r.usuarioId().toString(), null))));
        }
        if (campo == null) return null;
        Map<String, Object> no = no("people.route", "Rotear: " + politica.nome(), ids);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("value", "{{ trigger.outputs." + campo + " }}");
        params.put("routes", rotas);
        params.put("fallback", padrao);
        params.put("match", "first");
        params.put("compare", "eq");
        no.put("params", params);
        return no;
    }

    /** Política complexa: cadeia de condições, um aviso por rota. */
    private Map<String, Object> cadeia(RegraLegada.Politica politica, int indice, RegraLegada regra, List<Object> outros,
                                       boolean limiar, Set<String> ids) {
        List<RegraLegada.Rota> especificas = politica.rotas().stream().filter(r -> !r.padrao()).toList();
        RegraLegada.Rota padrao = politica.rotas().stream().filter(RegraLegada.Rota::padrao).findFirst().orElse(null);
        if (indice >= especificas.size()) {
            List<Object> dests = new ArrayList<>(outros);
            if (padrao != null) dests.add(dest("usuario", padrao.usuarioId().toString(), null));
            return avisar(regra, dests, limiar, ids, "padrão");
        }
        RegraLegada.Rota rota = especificas.get(indice);
        Map<String, Object> condicao = no("control.condition", "Rota " + (indice + 1) + ": " + politica.nome(), ids);
        List<Object> regras = new ArrayList<>();
        for (RegraLegada.Condicao c : rota.condicoes()) regras.add(regraCondicao(c));
        condicao.put("params", new LinkedHashMap<>(Map.of("condition", Map.of("op", "and", "rules", regras))));
        List<Object> dests = new ArrayList<>(outros);
        dests.add(dest("usuario", rota.usuarioId().toString(), null));
        condicao.put("then", new ArrayList<>(List.of(avisar(regra, dests, limiar, ids, "rota " + (indice + 1)))));
        condicao.put("else", new ArrayList<>(List.of(cadeia(politica, indice + 1, regra, outros, limiar, ids))));
        return condicao;
    }

    private Map<String, Object> avisar(RegraLegada regra, List<Object> recipients, boolean limiar, Set<String> ids, String sufixo) {
        Map<String, Object> no = no("notify.people", "Avisar" + (sufixo == null ? "" : " (" + sufixo + ")"), ids);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("recipients", new ArrayList<>(recipients));
        params.put("title", modelo(regra.titulo()));
        params.put("body", modelo(regra.corpo()));
        params.put("link", modelo(regra.deeplink() == null ? "" : regra.deeplink()));
        params.put("email", regra.enviarEmail());
        params.put("category", regra.categoria());
        params.put("priority", regra.prioridade());
        params.put("silence_minutes", limiar ? 0 : regra.cooldownMinutos());
        params.put("silence_key", "{{ trigger.outputs.id_referencia }}");
        no.put("params", params);
        return no;
    }

    /** {{variavel}} → {{ trigger.outputs.variavel }}. */
    static String modelo(String texto) {
        if (texto == null) return "";
        Matcher m = VARIAVEL.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("{{ trigger.outputs." + m.group(1) + " }}"));
        m.appendTail(sb);
        return sb.toString();
    }

    private static String cortar(String texto, int limite) {
        return texto.length() > limite ? texto.substring(0, limite) : texto;
    }
}
