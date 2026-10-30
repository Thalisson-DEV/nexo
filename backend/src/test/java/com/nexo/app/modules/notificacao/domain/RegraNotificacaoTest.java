package com.nexo.app.modules.notificacao.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegraNotificacaoTest {

    @Test
    void deveRecusarTemplateComVariavelQueOGatilhoNaoFornece() {
        assertThatThrownBy(() -> new RegraNotificacao(
                GatilhoNotificacao.SST_DESVIOS_PENDENTES, "Desvios", null, null, null,
                "Atenção", "Há {{quantidadeDeCoisas}} desvios.", null, 5, 60, false, false))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("quantidadeDeCoisas");
    }

    @Test
    void deveAceitarTemplateComAsVariaveisDeclaradas() {
        RegraNotificacao regra = regraPorLimiar();

        assertThat(regra.getCorpoTemplate()).contains("{{total}}", "{{limiar}}");
        assertThat(regra.getModo()).isEqualTo(ModoGatilho.POR_LIMIAR);
    }

    @Test
    void condicaoDeveUsarApenasCamposDoGatilho() {
        RegraNotificacao regra = regraPorLimiar();

        assertThatThrownBy(() -> regra.adicionarCondicao("gravidade", OperadorCondicao.IGUAL, "ALTA"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("gravidade");

        regra.adicionarCondicao("total", OperadorCondicao.MAIOR_QUE, "3");
        assertThat(regra.condicoesAtivas()).hasSize(1);
    }

    @Test
    void papelDinamicoNaoDeveExistirEmGatilhoPorLimiar() {
        RegraNotificacao regra = regraPorLimiar();

        assertThatThrownBy(() -> regra.adicionarDestinatario(
                TipoDestinatarioRegra.PAPEL_DINAMICO, null, "solicitante"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("por ocorrência");
    }

    @Test
    void papelDinamicoDeveExistirNoCatalogoDoGatilho() {
        RegraNotificacao regra = regraPorOcorrencia();

        assertThatThrownBy(() -> regra.adicionarDestinatario(
                TipoDestinatarioRegra.PAPEL_DINAMICO, null, "diretor"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("diretor");

        regra.adicionarDestinatario(TipoDestinatarioRegra.PAPEL_DINAMICO, null, "solicitante");
        assertThat(regra.destinatariosAtivos()).hasSize(1);
    }

    @Test
    void limiarDeveSerIgnoradoEmGatilhoPorOcorrencia() {
        RegraNotificacao regra = new RegraNotificacao(
                GatilhoNotificacao.DESCONTO_SOLICITADO, "Confirmação", null, null, null,
                "Registrado", "Seu desconto por {{motivo}} foi registrado.", null,
                99, 0, false, true);

        assertThat(regra.getLimiar())
                .as("não haveria o que comparar com um limiar num gatilho por ocorrência")
                .isNull();
    }

    @Test
    void regraSemDestinatarioNaoDeveSerUtilizavel() {
        RegraNotificacao regra = regraPorLimiar();

        assertThatThrownBy(regra::exigirUtilizavel)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("destinatário");
    }

    @Test
    void regraPorLimiarSemLimiarNaoDeveSerUtilizavel() {
        RegraNotificacao regra = new RegraNotificacao(
                GatilhoNotificacao.SST_DESVIOS_PENDENTES, "Desvios", null, null, null,
                "Atenção", "Há {{total}} desvios.", null, null, 60, false, false);
        regra.adicionarDestinatario(TipoDestinatarioRegra.PERMISSAO, null, "sst:desvio:tratar");

        assertThatThrownBy(regra::exigirUtilizavel)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("limiar");
    }

    @Test
    void destinatarioDeveExigirOAlvoQueOTipoPede() {
        RegraNotificacao regra = regraPorLimiar();

        assertThatThrownBy(() -> regra.adicionarDestinatario(TipoDestinatarioRegra.PERFIL, null, null))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> regra.adicionarDestinatario(TipoDestinatarioRegra.PERMISSAO, UUID.randomUUID(), " "))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void categoriaAusenteDeveHerdarADoGatilho() {
        RegraNotificacao regra = regraPorLimiar();

        assertThat(regra.getCategoria()).isEqualTo(CategoriaNotificacao.SEGURANCA);
    }

    private RegraNotificacao regraPorLimiar() {
        return new RegraNotificacao(
                GatilhoNotificacao.SST_DESVIOS_PENDENTES, "Desvios acumulados", null, null,
                PrioridadeNotificacao.ALTA, "Desvios exigem atenção",
                "Há {{total}} desvios sem resolução, acima de {{limiar}}.", null, 10, 720, false, false);
    }

    private RegraNotificacao regraPorOcorrencia() {
        return new RegraNotificacao(
                GatilhoNotificacao.DESCONTO_SOLICITADO, "Confirmação", null, null, null,
                "Desconto registrado", "Seu desconto por {{motivo}} foi registrado.", null,
                null, 0, false, false);
    }
}
