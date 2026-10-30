package com.nexo.app.modules.assistente.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.files.ArquivoParser;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeWebFetcherTest {

    private final SafeWebFetcher fetcher = new SafeWebFetcher(new ArquivoParser(new ObjectMapper()));

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/", "http://localhost:8080/actuator", "http://169.254.169.254/latest/meta-data",
            "http://10.0.0.5/", "http://192.168.1.10/", "http://172.20.0.3/", "http://[::1]/", "http://100.64.1.1/",
            "http://metadata.google.internal/", "http://0.0.0.0/", "http://[fd00::1]/", "http://[::ffff:127.0.0.1]/",
            "https://example.com:5432/"})
    void bloqueiaEnderecosInternosMetadadosEPortasNaoWeb(String url) {
        SafeWebFetcher.WebPage pagina = fetcher.abrir(url);
        assertThat(pagina.sucesso()).isFalse();
        assertThat(pagina.falha()).containsAnyOf("protecao de rede", "erro de rede");
    }

    @Test
    void aceitaSomenteHttpEHttpsSemCredenciais() {
        assertThatThrownBy(() -> SafeWebFetcher.normalizar("file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeWebFetcher.normalizar("ftp://example.com/x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeWebFetcher.normalizar("https://user:senha@example.com/")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SafeWebFetcher.normalizar("www.gov.br/trabalho").toString()).isEqualTo("https://www.gov.br/trabalho");
    }

    @Test
    void classificaEnderecosPublicosEPrivados() throws Exception {
        assertThat(SafeWebFetcher.publico(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SafeWebFetcher.publico(InetAddress.getByName("10.1.2.3"))).isFalse();
        assertThat(SafeWebFetcher.publico(InetAddress.getByName("169.254.169.254"))).isFalse();
        assertThat(SafeWebFetcher.publico(InetAddress.getByName("100.100.1.1"))).isFalse();
        assertThat(SafeWebFetcher.publico(InetAddress.getByName("2001:4860:4860::8888"))).isTrue();
        assertThat(SafeWebFetcher.publico(InetAddress.getByName("fe80::1"))).isFalse();
    }

    @Test
    void validacaoDeDestinoFalhaParaHostInterno() {
        assertThatThrownBy(() -> SafeWebFetcher.validarDestino(URI.create("http://localhost/")))
                .isInstanceOf(SafeWebFetcher.SsrfException.class);
    }

    @Test
    void extraiTextoLegivelComEstruturaERemoveNavegacaoEScripts() {
        String html = "<html><head><title>NR-10</title><script>alert(1)</script></head><body><nav>Menu</nav>"
                + "<main><h1>Segurança em eletricidade</h1><p>Primeiro paragrafo.</p><ul><li>Item A</li><li>Item B</li></ul>"
                + "<table><tr><td>Col 1</td><td>Col 2</td></tr></table></main><footer>Rodape</footer></body></html>";
        String texto = SafeWebFetcher.textoLegivel(Jsoup.parse(html).selectFirst("main"));
        assertThat(texto).contains("# Segurança em eletricidade", "Primeiro paragrafo.", "- Item A", "- Item B", "Col 1 | Col 2")
                .doesNotContain("alert").doesNotContain("Menu");
    }
}
