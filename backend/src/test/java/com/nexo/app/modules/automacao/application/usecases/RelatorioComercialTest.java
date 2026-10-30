package com.nexo.app.modules.automacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;

class RelatorioComercialTest {

    private final ModelosAutomacao modelos = new ModelosAutomacao();

    @Test
    void modeloTrazAsConsultasDaSemanaAntesDaAnalise() {
        Map<String, Object> definicao = modelos.relatorioComercialPorBase().definicao();
        DefinicaoAutomacao lida = DefinicaoAutomacao.ler(definicao);
        String json = Valores.paraJson(lida.paraMapa());
        for (String passo : List.of("q_equipes", "q_equipes_anterior", "q_turnos", "q_materiais", "q_solicitacoes", "q_metas", "analise", "enviar")) {
            assertThat(json).contains("\"" + passo + "\"");
        }
        assertThat(json).contains("execucoes_materiais").contains("metas_faturamento").contains("COMERCIAL");
        assertThat(json.indexOf("\"q_metas\"")).isLessThan(json.indexOf("\"analise\""));
    }

    @Test
    void atualizacaoMantemDestinatariosBasesAgendaEAvisoDeFalha() {
        Map<String, Object> rh = Map.of("tipo", "usuario", "valor", "8fa62a56-d354-442b-bbab-55945528faf9");
        Map<String, Object> rotas = new LinkedHashMap<>(Map.of("value", "{{ item }}",
                "routes", List.of(Map.of("when", "JUAZEIRO", "recipients", List.of(rh))), "fallback", List.of()));
        Map<String, Object> quemRecebe = new LinkedHashMap<>(Map.of("id", "quem_recebe", "type", "people.route", "params", rotas));
        Map<String, Object> porBase = new LinkedHashMap<>(Map.of("id", "cada_base", "type", "control.foreach",
                "params", Map.of("items", "{{ split(vars.bases, ';') }}"), "steps", List.of(quemRecebe)));
        Map<String, Object> antiga = new LinkedHashMap<>();
        antiga.put("trigger", Map.of("type", "trigger.schedule", "params", Map.of("frequency", "week", "weekdays", List.of(1), "time", "08:30")));
        antiga.put("variables", List.of(Map.of("name", "bases", "type", "string", "value", "JUAZEIRO;REMANSO")));
        antiga.put("settings", Map.of("notify_on_failure", List.of("ti@exemplo.com.br"), "timeout_hours", 168));
        antiga.put("steps", new ArrayList<>(List.of(porBase)));

        Map<String, Object> nova = Valores.mapa(modelos.relatorioComercialPorBase().definicao());
        AutomacoesIniciais.manterConfiguracao(antiga, nova);

        String json = Valores.paraJson(nova);
        assertThat(json).contains("8fa62a56-d354-442b-bbab-55945528faf9").contains("JUAZEIRO;REMANSO")
                .contains("08:30").contains("ti@exemplo.com.br").contains("q_equipes");
        assertThat(json).doesNotContain("\"when\":\"BONFIM\"");
    }
}
