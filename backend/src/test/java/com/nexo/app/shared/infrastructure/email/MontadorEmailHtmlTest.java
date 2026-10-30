package com.nexo.app.shared.infrastructure.email;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o único lugar do sistema que escreve marcação de e-mail.
 *
 * <p>Boa parte das asserções aqui é sobre o que o HTML <b>não</b> tem. Isso é
 * proposital: o defeito que originou este layout não era um bug, era o e-mail parecer
 * peça de marketing — e cada traço daquele registro (pílula colorida, emoji, rodapé de
 * lista de e-mails) é uma linha que alguém pode reintroduzir sem perceber que está
 * mudando como a empresa soa. Teste é o que transforma essa decisão em contrato.</p>
 */
class MontadorEmailHtmlTest {

    private EmailProperties propriedades;
    private MontadorEmailHtml montador;

    @BeforeEach
    void preparar() {
        propriedades = new EmailProperties();
        propriedades.setUrlBase("https://app.exemplo.com.br");
        montador = new MontadorEmailHtml(propriedades);
    }

    private ConteudoEmail.ConteudoEmailBuilder completo() {
        return ConteudoEmail.builder()
                .etiqueta("Aprovações")
                .prioridade("Prioridade alta")
                .destaque(DestaqueEmail.ATENCAO)
                .titulo("Desconto aguardando validação")
                .paragrafos(List.of("Um desconto por Avaria em 3x aguarda validação."))
                .dados(List.of(new ConteudoEmail.Dado("Motivo", "Avaria")))
                .rotuloAcao("Analisar e decidir")
                .urlAcao("https://app.exemplo.com.br/dp/descontos/abc")
                .anexos(List.of(new ConteudoEmail.Anexo("termo.pdf", 245_760, true)));
    }

    @Test
    void naoUsaOVocabularioVisualDeMarketing() {
        String html = montador.montar(completo().build());

        // Pílula: a etiqueta com fundo colorido e cantos totalmente arredondados.
        assertThat(html).doesNotContain("border-radius:999px");
        // Clipe de papel e qualquer outro pictograma no lugar de palavra.
        assertThat(html).doesNotContain("&#128206;");
        // A fórmula do rodapé de lista de e-mails.
        assertThat(html).doesNotContain("Você recebeu este e-mail porque");
    }

    @Test
    void declaraTemaClaroParaQueOClienteNaoInvertaAsCores() {
        String html = montador.montar(completo().build());

        // Sem <head> não há onde declarar nada — foi o defeito original.
        assertThat(html).startsWith("<!DOCTYPE html>").contains("<head>");
        assertThat(html)
                .contains("<meta name=\"color-scheme\" content=\"light\">")
                .contains("<meta name=\"supported-color-schemes\" content=\"light\">")
                // 'only' é o que diz "não adapte", em vez de "prefiro claro".
                .contains("color-scheme:light only");
    }

    @Test
    void desfazAReescritaDeCoresDoOutlookComPorAtributoMarcado() {
        String html = montador.montar(completo().build());

        // O Outlook.com troca as cores e marca com data-ogsc/data-ogsb o que tocou.
        assertThat(html).contains("[data-ogsc]").contains("[data-ogsb]");
        assertThat(html).contains("ces-cartao").contains("ces-superficie")
                .contains("ces-botao").contains("ces-forte").contains("ces-suave");
    }

    @Test
    void identificaAreaEPrioridadeEmTexto() {
        String html = montador.montar(completo().build());

        assertThat(html).contains("Aprovações").contains("Prioridade alta");
        // A cor entra na palavra, não num fundo atrás dela.
        assertThat(html).contains("color:" + DestaqueEmail.ATENCAO.getTexto());
        assertThat(html).doesNotContain("background-color:" + DestaqueEmail.ATENCAO.getTexto());
    }

    @Test
    void usaAPaletaDoFrontend() {
        String html = montador.montar(completo().build());

        assertThat(html).contains("#1E3A8A");  // azul da marca, da logo
        assertThat(html).contains("#f1f5f9");  // slate-100, fundo da aplicação
        assertThat(html).contains("#0f172a");  // slate-900, texto forte
    }

    @Test
    void derivaOLogoDaRaizDoFrontendQuandoNaoHaUrlPropria() {
        String html = montador.montar(completo().build());

        assertThat(html).contains("https://app.exemplo.com.br/assets/logo-email.png");
    }

    @Test
    void aUrlDeLogoConfiguradaVenceADerivada() {
        propriedades.setLogoUrl("https://cdn.exemplo/marca.png");

        String html = montador.montar(completo().build());

        assertThat(html).contains("https://cdn.exemplo/marca.png");
        assertThat(html).doesNotContain("/assets/logo-email.png");
    }

    @Test
    void mantemAMarcaEmTextoAoLadoDoLogo() {
        // Outlook corporativo bloqueia imagem remota por padrão. Sem o texto, o
        // cabeçalho chegaria vazio — e e-mail sem remetente identificável parece golpe.
        String html = montador.montar(completo().build());

        assertThat(html).contains(">Nexo<").contains("Gestão Operacional");
    }

    @Test
    void omiteOsBlocosQueNaoSeAplicam() {
        String html = montador.montar(ConteudoEmail.builder()
                .titulo("Aviso simples")
                .build());

        assertThat(html).doesNotContain("Anexos");
        assertThat(html).doesNotContain("Caso o botão não funcione");
        assertThat(html).contains("Aviso simples");
    }

    @Test
    void escapaValorInterpoladoParaQueOTemplateNaoViajeComoMarcacao() {
        String html = montador.montar(completo()
                .titulo("<script>alert('x')</script>")
                .build());

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void aVersaoEmTextoCarregaAMesmaInformacaoDoHtml() {
        String texto = montador.montarTexto(completo().build());

        assertThat(texto)
                .contains("APROVAÇÕES · PRIORIDADE ALTA")
                .contains("Desconto aguardando validação")
                .contains("Motivo: Avaria")
                .contains("https://app.exemplo.com.br/dp/descontos/abc")
                .contains("termo.pdf (240 KB)")
                .contains("Não responda a este e-mail");
    }

    @Test
    void anexoQueNaoCoubeApareceMarcadoEmVezDeSumir() {
        String html = montador.montar(completo()
                .anexos(List.of(new ConteudoEmail.Anexo("laudo.pdf", 12_000_000, false)))
                .build());

        assertThat(html).contains("laudo.pdf").contains("disponível no sistema");
    }

    @Test
    void semRaizDoFrontendOEmailSaiSemBotaoEmVezDeComLinkQuebrado() {
        propriedades.setUrlBase(null);

        String html = montador.montar(ConteudoEmail.builder()
                .titulo("Aviso")
                .urlAcao(propriedades.resolverUrl("/dp/descontos/abc"))
                .build());

        assertThat(html).doesNotContain("href=\"/dp/descontos/abc\"");
        assertThat(html).doesNotContain("Preferências de notificação");
    }
}
