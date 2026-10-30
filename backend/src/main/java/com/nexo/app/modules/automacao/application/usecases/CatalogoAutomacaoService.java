package com.nexo.app.modules.automacao.application.usecases;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.automacao.application.motor.Catalogo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecCampo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Condicoes;
import com.nexo.app.modules.automacao.domain.expressao.Funcoes;
import com.nexo.app.modules.notificacao.NotificacaoApi;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;

/**
 * O catálogo no formato que o designer consome: nós com parâmetros e saídas, grupos,
 * tipos de automação, operadores das condições, funções das expressões e os blocos do
 * editor de e-mail. É uma visão — o catálogo de verdade continua em código.
 */
@Service
public class CatalogoAutomacaoService {

    public static final Map<String, String> BLOCOS = new LinkedHashMap<>();

    static {
        BLOCOS.put("markdown", "Texto formatado (ex.: análise do Copiloto)");
        BLOCOS.put("table", "Tabela de uma lista");
        BLOCOS.put("list", "Lista com marcadores");
        BLOCOS.put("html", "HTML pronto de outro passo");
        BLOCOS.put("button", "Botão com link");
    }

    private final Catalogo catalogo;
    private final ServicoEmail email;
    private final CopilotoApi copiloto;
    private final NotificacaoApi notificacoes;

    public CatalogoAutomacaoService(Catalogo catalogo, ServicoEmail email, CopilotoApi copiloto, NotificacaoApi notificacoes) {
        this.catalogo = catalogo;
        this.email = email;
        this.copiloto = copiloto;
        this.notificacoes = notificacoes;
    }

    public Map<String, Object> visao(List<Map<String, Object>> automacoes) {
        List<Map<String, Object>> nos = new ArrayList<>();
        for (EspecNo no : catalogo.todos()) nos.add(no(no));
        List<Map<String, Object>> tipos = new ArrayList<>();
        for (TipoAutomacao t : TipoAutomacao.values()) {
            tipos.add(Map.of("valor", t.name(), "rotulo", t.getRotulo(), "descricao", t.getDescricao()));
        }
        Map<String, Object> visao = new LinkedHashMap<>();
        visao.put("nos", nos);
        visao.put("grupos", Catalogo.GRUPOS);
        visao.put("tipos", tipos);
        visao.put("operadores", Condicoes.OPERADORES);
        visao.put("funcoes", Funcoes.ajuda());
        visao.put("blocos", BLOCOS);
        visao.put("emailPronto", email.ativo());
        visao.put("iaPronta", copiloto.disponivel());
        visao.put("categorias", notificacoes.categorias());
        visao.put("prioridades", notificacoes.prioridades());
        visao.put("automacoes", automacoes);
        return visao;
    }

    public static Map<String, Object> no(EspecNo no) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", no.tipo());
        m.put("grupo", no.grupo());
        m.put("subgrupo", no.subgrupo());
        m.put("rotulo", no.rotulo());
        m.put("descricao", no.descricao());
        m.put("icone", no.icone());
        List<Map<String, Object>> params = new ArrayList<>();
        for (EspecParam p : no.params()) params.add(param(p));
        m.put("params", params);
        List<Map<String, Object>> saidas = new ArrayList<>();
        for (EspecSaida s : no.saidas()) saidas.add(saida(s));
        m.put("saidas", saidas);
        m.put("conteiner", no.conteiner());
        m.put("gatilho", no.gatilho());
        m.put("efeitoExterno", no.efeitoExterno());
        m.put("repeticaoPadrao", Map.of("policy", no.repeticaoPadrao().politica(), "count", no.repeticaoPadrao().quantidade(),
                "interval_seconds", no.repeticaoPadrao().intervaloSegundos()));
        m.put("ia", no.ia());
        m.put("palavras", no.palavrasChave());
        m.put("saidasDinamicas", no.saidasDinamicas());
        m.put("chaveEvento", no.chaveEvento());
        m.put("satisfaz", no.satisfaz().stream().map(Enum::name).toList());
        return m;
    }

    private static Map<String, Object> param(EspecParam p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chave", p.chave());
        m.put("rotulo", p.rotulo());
        m.put("tipo", p.tipo());
        m.put("obrigatorio", p.obrigatorio());
        m.put("padrao", p.padrao());
        m.put("ajuda", p.ajuda());
        m.put("placeholder", p.placeholder());
        m.put("opcoes", p.opcoes());
        m.put("dinamico", p.dinamico());
        m.put("minimo", p.minimo());
        m.put("maximo", p.maximo());
        List<Map<String, Object>> campos = new ArrayList<>();
        for (EspecCampo c : p.campos()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("chave", c.chave());
            cm.put("rotulo", c.rotulo());
            cm.put("tipo", c.tipo());
            cm.put("opcoes", c.opcoes());
            cm.put("placeholder", c.placeholder());
            campos.add(cm);
        }
        m.put("campos", campos);
        m.put("mostrarSe", p.mostrarSe());
        m.put("avancado", p.avancado());
        return m;
    }

    private static Map<String, Object> saida(EspecSaida s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chave", s.chave());
        m.put("rotulo", s.rotulo());
        m.put("tipo", s.tipo());
        m.put("descricao", s.descricao());
        List<Map<String, Object>> campos = new ArrayList<>();
        for (EspecSaida c : s.campos()) campos.add(saida(c));
        m.put("campos", campos);
        return m;
    }

    /** Resumo do gatilho para listas ("Toda segunda às 08:00", "Quando: Desconto validado"). */
    public String resumoGatilho(String tipo, Map<String, Object> params) {
        EspecNo spec = catalogo.buscar(tipo);
        if (spec == null) return tipo;
        if (spec.resumo() != null) {
            try {
                return spec.resumo().apply(params);
            } catch (RuntimeException e) {
                return spec.rotulo();
            }
        }
        return spec.rotulo();
    }

    public String rotuloGatilho(String tipo) {
        EspecNo spec = catalogo.buscar(tipo);
        return spec == null ? tipo : spec.rotulo();
    }
}
