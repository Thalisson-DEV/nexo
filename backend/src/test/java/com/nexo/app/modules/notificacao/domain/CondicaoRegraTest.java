package com.nexo.app.modules.notificacao.domain;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CondicaoRegraTest {

    @Test
    void comparacaoNumericaNaoDeveCairEmOrdemAlfabetica() {
        CondicaoRegra condicao = condicao(OperadorCondicao.MAIOR_QUE, "9");

        assertThat(condicao.satisfeitaPor(10))
                .as("como texto, \"10\" viria antes de \"9\" e a regra nunca dispararia")
                .isTrue();
        assertThat(condicao.satisfeitaPor(8)).isFalse();
    }

    @Test
    void valorAusenteNuncaDeveSatisfazerComparacaoNumerica() {
        assertThat(condicao(OperadorCondicao.MAIOR_QUE, "5").satisfeitaPor(null)).isFalse();
        assertThat(condicao(OperadorCondicao.MENOR_QUE, "5").satisfeitaPor(null))
                .as("tratar ausência como zero faria a regra disparar por falta de dado")
                .isFalse();
        assertThat(condicao(OperadorCondicao.MAIOR_OU_IGUAL, "5").satisfeitaPor("abc")).isFalse();
        assertThat(condicao(OperadorCondicao.MENOR_OU_IGUAL, "5").satisfeitaPor("abc")).isFalse();
    }

    @Test
    void igualdadeDeveCompararNumerosComoNumerosETextoComoTexto() {
        assertThat(condicao(OperadorCondicao.IGUAL, "10").satisfeitaPor("10.0")).isTrue();
        assertThat(condicao(OperadorCondicao.IGUAL, "ALTA").satisfeitaPor("alta")).isTrue();
        assertThat(condicao(OperadorCondicao.DIFERENTE, "ALTA").satisfeitaPor("BAIXA")).isTrue();
    }

    @Test
    void contemEEmDevemIgnorarMaiusculas() {
        assertThat(condicao(OperadorCondicao.CONTEM, "epi").satisfeitaPor("Dano em EPI")).isTrue();
        assertThat(condicao(OperadorCondicao.EM, "ALTA, CRITICA").satisfeitaPor("critica")).isTrue();
        assertThat(condicao(OperadorCondicao.EM, "ALTA, CRITICA").satisfeitaPor("BAIXA")).isFalse();
    }

    @Test
    void deveRecusarValorNaoNumericoEmOperadorNumerico() {
        assertThatThrownBy(() -> condicao(OperadorCondicao.MAIOR_QUE, "muitos"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("exige um número");
    }

    private CondicaoRegra condicao(OperadorCondicao operador, String valor) {
        RegraNotificacao regra = new RegraNotificacao(
                GatilhoNotificacao.SST_DESVIOS_PENDENTES, "Desvios", null, null, null,
                "Atenção", "Há {{total}} desvios.", null, 5, 60, false, false);
        return new CondicaoRegra(regra, "total", operador, valor);
    }
}
