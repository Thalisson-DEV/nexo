package com.nexo.app.modules.automacao.domain.expressao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpressoesTest {

    private static Map<String, Object> escopo() {
        Map<String, Object> e = new HashMap<>();
        e.put("trigger", Map.of("outputs", Map.of(
                "colaborador", Map.of("nome", "Maria Souza", "base", "Juazeiro"),
                "valorValidado", 1234.5,
                "parcelas", 3)));
        e.put("steps", Map.of("buscar", Map.of("outputs", Map.of("items", List.of(
                Map.of("equipe", "A1", "valor", 10), Map.of("equipe", "B2", "valor", "20,5"))))));
        e.put("vars", Map.of("limite", 15));
        return e;
    }

    @Test
    void valorInteiroMantemOTipoEMisturadoViraTexto() {
        Object lista = Expressoes.renderizar("{{ steps.buscar.outputs.items }}", escopo());
        assertThat(lista).isInstanceOf(List.class);
        assertThat(Expressoes.renderizar("Base: {{ trigger.outputs.colaborador.base }}!", escopo())).isEqualTo("Base: Juazeiro!");
        assertThat(Expressoes.renderizar("{{ trigger.outputs.parcelas }}", escopo())).isEqualTo(3);
    }

    @Test
    void funcoesEAritmetica() {
        assertThat(Expressoes.avaliar("sum(steps.buscar.outputs.items, 'valor')", escopo())).isEqualTo(30.5);
        assertThat(Expressoes.avaliar("format_money(trigger.outputs.valorValidado)", escopo())).isEqualTo("R$ 1.234,50");
        assertThat(Expressoes.avaliar("length(steps.buscar.outputs.items) * 2 + 1", escopo())).isEqualTo(5L);
        assertThat(Expressoes.avaliar("if(trigger.outputs.parcelas > vars.limite, 'muito', 'ok')", escopo())).isEqualTo("ok");
        assertThat(Expressoes.avaliar("upper(trigger.outputs.colaborador.base) == 'JUAZEIRO'", escopo())).isEqualTo(true);
        assertThat(Expressoes.avaliar("steps.buscar.outputs.items[1].equipe", escopo())).isEqualTo("B2");
        assertThat(Expressoes.avaliar("join(pluck(steps.buscar.outputs.items, 'equipe'), ' / ')", escopo())).isEqualTo("A1 / B2");
    }

    @Test
    void caminhoAusenteDaNuloEnaoErro() {
        assertThat(Expressoes.avaliar("trigger.outputs.nao_existe.campo", escopo())).isNull();
        assertThat(Expressoes.renderizar("[{{ trigger.outputs.nada }}]", escopo())).isEqualTo("[]");
    }

    @Test
    void naoExecutaNadaForaDoCatalogo() {
        assertThatThrownBy(() -> Expressoes.analisar("Runtime.getRuntime()")).isInstanceOf(ErroExpressao.class);
        assertThatThrownBy(() -> Expressoes.analisar("exec('rm')")).isInstanceOf(ErroExpressao.class);
    }

    @Test
    void referenciasParaOVerificador() {
        List<Expressoes.Referencia> refs = Expressoes.referencias("Olá {{ steps.buscar.outputs.items }} e {{ vars.limite }}");
        assertThat(refs).extracting(Expressoes.Referencia::raiz).containsExactly("steps", "vars");
        assertThat(refs.get(0).nome()).isEqualTo("buscar");
        assertThat(refs.get(0).resto()).containsExactly("outputs", "items");
    }

    @Test
    void condicoesComGruposEouSemDiferenciarMaiusculas() {
        Map<String, Object> grupo = Map.of("op", "and", "rules", List.of(
                Map.of("left", "{{ trigger.outputs.colaborador.base }}", "operator", "in", "right", "juazeiro; petrolina"),
                Map.of("op", "or", "rules", List.of(
                        Map.of("left", "{{ trigger.outputs.valorValidado }}", "operator", "gt", "right", "5000"),
                        Map.of("left", "{{ trigger.outputs.parcelas }}", "operator", "gte", "right", "3")))));
        assertThat(Condicoes.avaliar(grupo, escopo())).isTrue();
        assertThat(Condicoes.avaliar(Map.of("op", "and", "rules", List.of(
                Map.of("left", "{{ trigger.outputs.colaborador.base }}", "operator", "eq", "right", "Petrolina"))), escopo())).isFalse();
        assertThat(Condicoes.avaliar(Map.of(), escopo())).as("sem regras = sempre").isTrue();
    }

    @Test
    void numerosEmTextoBrasileiro() {
        assertThat(Valores.numero("R$ 1.234,56")).isEqualTo(1234.56);
        assertThat(Valores.numero("10")).isEqualTo(10L);
        assertThat(Condicoes.comparar("gt", "10", "9")).as("10 > 9 como número, não como texto").isTrue();
    }

    @Test
    void booleanoDoGatilhoComparadoComTexto() {
        Map<String, Object> escopo = Map.of("trigger", Map.of("outputs", Map.of("tipo", "PAGAMENTO", "obraCivil", false)));
        Map<String, Object> regraConvertida = Map.of("op", "and", "rules", List.of(
                Map.of("left", "{{ trigger.outputs.tipo }}", "operator", "eq", "right", "PAGAMENTO"),
                Map.of("left", "{{ trigger.outputs.obraCivil }}", "operator", "eq", "right", "false")));
        assertThat(Condicoes.avaliar(regraConvertida, escopo)).as("regra convertida do financeiro").isTrue();
        assertThat(Condicoes.comparar("eq", true, "true")).isTrue();
        assertThat(Condicoes.comparar("eq", false, "true")).isFalse();
        assertThat(Condicoes.comparar("ne", false, "true")).isTrue();
        assertThat(Condicoes.comparar("eq", true, "Sim")).isTrue();
        assertThat(Condicoes.comparar("eq", false, "não")).isTrue();
        assertThat(Condicoes.comparar("eq", "PAGAMENTO", "true")).as("texto comum segue como texto").isFalse();
    }
}
