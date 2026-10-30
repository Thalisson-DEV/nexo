package com.nexo.app.modules.notificacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

class PoliticaRoteamentoNotificacaoTest {

    @Test
    void aceitaRotasOrdenadasComUmaCondicionalEUmFallback() {
        var politica = politica();
        var claudio = politica.adicionarRota(10, false, UUID.randomUUID());
        claudio.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        claudio.adicionarCondicao("base", OperadorCondicao.EM,
                "JUAZEIRO,REMANSO,JACOBINA,BONFIM");
        politica.adicionarRota(100, true, UUID.randomUUID());

        assertThatCode(politica::exigirUtilizavel).doesNotThrowAnyException();
        assertThat(claudio.satisfeitaPor(Map.of("frota", true, "base", "BONFIM"))).isTrue();
        assertThat(claudio.satisfeitaPor(Map.of("frota", false, "base", "BONFIM"))).isFalse();
    }

    @Test
    void exigeExatamenteUmFallbackSemCondicoesEOrdensUnicas() {
        var semFallback = politica();
        var especifica = semFallback.adicionarRota(10, false, UUID.randomUUID());
        especifica.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        assertThatThrownBy(semFallback::exigirUtilizavel)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("exatamente uma rota padrão");

        var fallbackCondicional = politica();
        var rota = fallbackCondicional.adicionarRota(10, false, UUID.randomUUID());
        rota.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        var padrao = fallbackCondicional.adicionarRota(20, true, UUID.randomUUID());
        padrao.adicionarCondicao("uf", OperadorCondicao.IGUAL, "SE");
        assertThatThrownBy(fallbackCondicional::exigirUtilizavel)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("padrão não pode");

        var ordemDuplicada = politica();
        var primeira = ordemDuplicada.adicionarRota(10, false, UUID.randomUUID());
        primeira.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        ordemDuplicada.adicionarRota(10, true, UUID.randomUUID());
        assertThatThrownBy(ordemDuplicada::exigirUtilizavel)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("ordens");
    }

    @Test
    void recusaCampoQueOGatilhoNaoPublica() {
        var rota = politica().adicionarRota(10, false, UUID.randomUUID());

        assertThatThrownBy(() -> rota.adicionarCondicao(
                "diretorHardcoded", OperadorCondicao.IGUAL, "Felipe"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("diretorHardcoded");
    }

    private static PoliticaRoteamentoNotificacao politica() {
        return new PoliticaRoteamentoNotificacao(
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA,
                "Diretoria financeira por base e frota", null);
    }
}
