package com.nexo.app.modules.automacao.application.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.nos.NosControle;
import com.nexo.app.modules.automacao.domain.StatusEtapa;
import com.nexo.app.modules.automacao.domain.StatusExecucao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;

class InterpretadorTest {

    /** Armazém em memória: o mesmo contrato do banco, para testar retomada. */
    static final class Memoria implements ArmazemExecucao {
        final Map<String, RegistroEtapa> registros = new HashMap<>();
        final Map<UUID, Map<String, Object>> aprovacoes = new HashMap<>();
        int salvamentos;

        @Override
        public Map<String, RegistroEtapa> carregar() {
            Map<String, RegistroEtapa> copia = new HashMap<>();
            registros.forEach((k, v) -> copia.put(k, v.copia()));
            return copia;
        }

        @Override
        public void salvar(RegistroEtapa registro) {
            salvamentos++;
            registros.put(registro.chave(), registro.copia());
        }

        @Override
        public boolean cancelada() {
            return false;
        }

        @Override
        public void sinalVital() {
        }

        @Override
        public UUID abrirAprovacao(RegistroEtapa registro, PedidoAprovacao pedido) {
            UUID id = UUID.randomUUID();
            aprovacoes.put(id, null);
            return id;
        }

        @Override
        public Map<String, Object> resultadoAprovacao(UUID aprovacaoId) {
            return aprovacoes.get(aprovacaoId);
        }

        @Override
        public void encerrarAprovacao(UUID aprovacaoId, String status) {
        }
    }

    private final AtomicInteger efeitos = new AtomicInteger();
    private final AtomicInteger falhasRestantes = new AtomicInteger();
    private final AtomicReference<OffsetDateTime> agora = new AtomicReference<>(OffsetDateTime.of(2026, 10, 12, 10, 0, 0, 0, ZoneOffset.ofHours(-3)));
    private Catalogo catalogo;

    @BeforeEach
    void preparar() {
        Catalogo.ProvedorNos teste = () -> List.of(
                EspecNo.de("trigger.manual", "trigger", "Manual", "", "play").gatilho()
                        .preparador((c, p, carga) -> new LinkedHashMap<>(carga)).criar(),
                EspecNo.de("test.eco", "data", "Eco", "", "x")
                        .params(EspecParam.de("value", "Valor", "text").criar())
                        .saidas(EspecSaida.de("value", "Valor", "any"))
                        .executor((ctx, p) -> {
                            Map<String, Object> r = new HashMap<>();
                            r.put("value", p.get("value"));
                            return r;
                        }).criar(),
                EspecNo.de("test.instavel", "integration", "Instável", "", "x")
                        .repeticao("fixed", 3, 60)
                        .executor((ctx, p) -> {
                            if (falhasRestantes.getAndDecrement() > 0) throw new ErroAcao("503", true);
                            return Map.of("ok", true);
                        }).criar(),
                EspecNo.de("test.efeito", "notify", "Efeito", "", "x").efeitoExterno()
                        .executor((ctx, p) -> {
                            efeitos.incrementAndGet();
                            return Map.of("enviado", true);
                        }).criar(),
                EspecNo.de("test.quebra", "data", "Quebra", "", "x")
                        .executor((ctx, p) -> {
                            throw new ErroAcao("deu ruim");
                        }).criar());
        List<Catalogo.ProvedorNos> provedores = new ArrayList<>();
        provedores.add(new NosControle());
        provedores.add(teste);
        catalogo = new Catalogo(provedores);
    }

    private Interpretador interpretador(Map<String, Object> definicao, Memoria armazem, Map<String, Object> gatilho) {
        return new Interpretador(DefinicaoAutomacao.ler(definicao), catalogo, armazem, gatilho,
                Map.of("id", "r1", "uuid", UUID.randomUUID()), Map.of("id", "a1", "uuid", UUID.randomUUID(), "name", "Teste"),
                "AUTOMATICA", agora::get, ms -> { }, new Interpretador.Identidade(UUID.randomUUID(), Set.of(), null),
                (valor, ctx) -> new Interpretador.ResolvedorPessoas.Pessoas(List.of(UUID.randomUUID()), List.of()), false);
    }

    private static Map<String, Object> no(String id, String tipo, Map<String, Object> params) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", tipo);
        n.put("params", params);
        return n;
    }

    private static Map<String, Object> definicao(Object... passos) {
        return Map.of("schema", 3, "trigger", Map.of("type", "trigger.manual", "params", Map.of()), "steps", List.of(passos));
    }

    @Test
    void condicaoRoteiaPeloValorDaEntidade() {
        Map<String, Object> condicao = no("base", "control.condition", Map.of("condition", Map.of("op", "and", "rules",
                List.of(Map.of("left", "{{ trigger.outputs.colaborador.base }}", "operator", "eq", "right", "Juazeiro")))));
        condicao.put("then", List.of(no("rh_juazeiro", "test.eco", Map.of("value", "rh.juazeiro@exemplo.com.br"))));
        condicao.put("else", List.of(no("rh_geral", "test.eco", Map.of("value", "rh@exemplo.com.br"))));
        Map<String, Object> depois = no("final", "test.eco", Map.of("value", "{{ steps.rh_juazeiro.outputs.value }}"));
        Memoria m = new Memoria();
        Interpretador.Desfecho d = interpretador(definicao(condicao, depois), m,
                Map.of("colaborador", Map.of("base", "juazeiro"))).executar();
        assertThat(d.status()).isEqualTo(StatusExecucao.SUCESSO);
        assertThat(m.registros.get("rh_juazeiro|").saidas.get("value")).isEqualTo("rh.juazeiro@exemplo.com.br");
        assertThat(m.registros.get("rh_geral|")).isNull();
        assertThat(m.registros.get("final|").saidas.get("value")).isEqualTo("rh.juazeiro@exemplo.com.br");
    }

    @Test
    void paraCadaGravaUmaEtapaPorItem() {
        Map<String, Object> laco = no("cada", "control.foreach", Map.of("items", "{{ trigger.outputs.bases }}"));
        laco.put("steps", List.of(no("eco", "test.eco", Map.of("value", "{{ item }}"))));
        Memoria m = new Memoria();
        interpretador(definicao(laco), m, Map.of("bases", List.of("Juazeiro", "Petrolina"))).executar();
        assertThat(m.registros.get("eco|/cada[0]").saidas.get("value")).isEqualTo("Juazeiro");
        assertThat(m.registros.get("eco|/cada[1]").saidas.get("value")).isEqualTo("Petrolina");
        assertThat(m.registros.get("cada|").status).isEqualTo(StatusEtapa.SUCESSO);
    }

    @Test
    void repeticaoComAtrasoSuspendeERetomaSemRepetirOQueJaFoi() {
        falhasRestantes.set(1);
        Map<String, Object> def = definicao(no("efeito", "test.efeito", Map.of()), no("instavel", "test.instavel", Map.of()));
        Memoria m = new Memoria();
        Interpretador.Desfecho primeira = interpretador(def, m, Map.of()).executar();
        assertThat(primeira.status()).isEqualTo(StatusExecucao.AGUARDANDO);
        assertThat(primeira.retomarEm()).isEqualTo(agora.get().plusSeconds(60));
        agora.set(agora.get().plusSeconds(61));
        Interpretador.Desfecho segunda = interpretador(def, m, Map.of()).executar();
        assertThat(segunda.status()).isEqualTo(StatusExecucao.SUCESSO);
        assertThat(efeitos.get()).as("o e-mail do passo anterior não sai duas vezes").isEqualTo(1);
        assertThat(m.registros.get("instavel|").tentativa).isEqualTo(2);
    }

    @Test
    void efeitoInterrompidoNoMeioNaoERepetido() {
        Memoria m = new Memoria();
        RegistroEtapa meio = new RegistroEtapa("efeito", "", "test.efeito", StatusEtapa.EXECUTANDO, agora.get());
        m.registros.put(meio.chave(), meio);
        Interpretador.Desfecho d = interpretador(definicao(no("efeito", "test.efeito", Map.of())), m, Map.of()).executar();
        assertThat(d.status()).isEqualTo(StatusExecucao.FALHA);
        assertThat(efeitos.get()).isZero();
        assertThat(m.registros.get("efeito|").erro).contains("não foi repetido");
    }

    @Test
    void escopoComTratarAposFalha() {
        Map<String, Object> tentar = no("tentar", "control.scope", Map.of());
        tentar.put("steps", List.of(no("quebra", "test.quebra", Map.of())));
        Map<String, Object> tratar = no("tratar", "control.scope", Map.of());
        tratar.put("run_after", List.of("failed"));
        tratar.put("steps", List.of(no("avisa", "test.eco", Map.of("value", "{{ steps.quebra.error }}"))));
        Memoria m = new Memoria();
        Interpretador.Desfecho d = interpretador(definicao(tentar, tratar), m, Map.of()).executar();
        assertThat(d.status()).isEqualTo(StatusExecucao.SUCESSO);
        assertThat(m.registros.get("avisa|").saidas.get("value")).isEqualTo("deu ruim");
    }

    @Test
    void aprovacaoSuspendeAteAlguemResponder() {
        Map<String, Object> aprovacao = no("aprovar", "approval.request", Map.of("title", "Pode enviar?", "approvers", List.of()));
        Map<String, Object> depois = no("depois", "test.eco", Map.of("value", "{{ steps.aprovar.outputs.outcome }}"));
        Catalogo.ProvedorNos comAprovacao = () -> List.of(EspecNo.de("approval.request", "approval", "Pedir aprovação", "", "check")
                .params(EspecParam.de("title", "t", "text").criar(), EspecParam.de("approvers", "a", "recipients").criar(),
                        EspecParam.de("expires_after_hours", "h", "number").padrao(0).criar())
                .criar());
        List<EspecNo> anteriores = catalogo.todos().stream().filter(n -> n.tipo().startsWith("test.") || n.gatilho()).toList();
        List<Catalogo.ProvedorNos> provedores = new ArrayList<>(List.of(new NosControle(), () -> anteriores, comAprovacao));
        catalogo = new Catalogo(provedores);
        Memoria m = new Memoria();
        Map<String, Object> def = definicao(aprovacao, depois);
        assertThat(interpretador(def, m, Map.of()).executar().status()).isEqualTo(StatusExecucao.AGUARDANDO);
        UUID id = m.aprovacoes.keySet().iterator().next();
        m.aprovacoes.put(id, Map.of("outcome", "Aprovar", "approved", true));
        assertThat(interpretador(def, m, Map.of()).executar().status()).isEqualTo(StatusExecucao.SUCESSO);
        assertThat(m.registros.get("depois|").saidas.get("value")).isEqualTo("Aprovar");
    }

    @Test
    void verificadorApontaReferenciaAPassoQueAindaNaoRodou() {
        Map<String, Object> def = definicao(no("antes", "test.eco", Map.of("value", "{{ steps.depois.outputs.value }}")),
                no("depois", "test.eco", Map.of("value", "x")));
        List<Verificador.Problema> problemas = new Verificador(catalogo).verificar(DefinicaoAutomacao.ler(def));
        assertThat(problemas).anyMatch(p -> p.severidade().equals("error") && p.noId().equals("antes"));
    }

    @Test
    void recorrenciaSemanalNoHorarioDeBrasilia() {
        Map<String, Object> p = Map.of("frequency", "week", "weekdays", List.of(0), "time", "07:00");
        OffsetDateTime domingo = OffsetDateTime.of(2026, 10, 11, 20, 0, 0, 0, ZoneOffset.ofHours(-3));
        assertThat(Recorrencia.proximo(p, domingo)).isEqualTo(OffsetDateTime.of(2026, 10, 12, 7, 0, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(Recorrencia.descrever(p)).isEqualTo("Toda segunda às 07:00");
    }
}
