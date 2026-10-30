package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.domain.CodigoAutorizacaoIa;
import com.nexo.app.modules.assistente.infrastructure.web.UrlPublicaIa;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras puras das conexoes de IA: escopos, enderecos de retorno, PKCE, credenciais e endereco publico. */
class ConexoesIaRegrasTest {

    @Test
    void escopoSoDeLeituraNuncaViraEscrita() {
        assertThat(OAuthIaService.pedeEscrita("ces.leitura")).isFalse();
        assertThat(OAuthIaService.pedeEscrita("ces.leitura ces.escrita")).isTrue();
        assertThat(OAuthIaService.pedeEscrita(null)).isTrue();
        assertThat(OAuthIaService.pedeEscrita("  ")).isTrue();
        assertThat(OAuthIaService.pedeEscrita("openid")).isTrue();
    }

    @Test
    void retornoAceitaHttpsLoopbackEEsquemaDeAplicativo() {
        assertThatCode(() -> OAuthIaService.validarRetorno("https://claude.ai/api/mcp/auth_callback")).doesNotThrowAnyException();
        assertThatCode(() -> OAuthIaService.validarRetorno("http://localhost:33418/callback")).doesNotThrowAnyException();
        assertThatCode(() -> OAuthIaService.validarRetorno("http://127.0.0.1:6274/oauth/callback")).doesNotThrowAnyException();
        assertThatCode(() -> OAuthIaService.validarRetorno("cursor://anysphere.cursor-retrieval/oauth/callback")).doesNotThrowAnyException();
    }

    @Test
    void retornoRecusaHttpExternoFragmentoEEsquemasPerigosos() {
        assertThatThrownBy(() -> OAuthIaService.validarRetorno("http://evil.com/cb")).isInstanceOf(OAuthErro.class);
        assertThatThrownBy(() -> OAuthIaService.validarRetorno("https://claude.ai/cb#x")).isInstanceOf(OAuthErro.class);
        assertThatThrownBy(() -> OAuthIaService.validarRetorno("javascript:alert(1)")).isInstanceOf(OAuthErro.class);
        assertThatThrownBy(() -> OAuthIaService.validarRetorno("data:text/html,x")).isInstanceOf(OAuthErro.class);
        assertThatThrownBy(() -> OAuthIaService.validarRetorno(null)).isInstanceOf(OAuthErro.class);
    }

    @Test
    void pkceS256ConfereSoOVerificadorCerto() throws Exception {
        String verificador = "a".repeat(50);
        String desafio = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII)));
        CodigoAutorizacaoIa codigo = new CodigoAutorizacaoIa("h", "ces-app-x", UUID.randomUUID(), false,
                "http://localhost/cb", desafio, null, OffsetDateTime.now().plusMinutes(5));

        assertThat(codigo.confereVerificador(verificador)).isTrue();
        assertThat(codigo.confereVerificador("b".repeat(50))).isFalse();
        assertThat(codigo.confereVerificador("curto")).isFalse();
        assertThat(codigo.confereVerificador(null)).isFalse();
    }

    @Test
    void codigoDeUsoUnicoEComPrazo() {
        CodigoAutorizacaoIa codigo = new CodigoAutorizacaoIa("h", "ces-app-x", UUID.randomUUID(), true,
                "http://localhost/cb", "d", null, OffsetDateTime.now().plusMinutes(5));
        assertThat(codigo.isUtilizavel(OffsetDateTime.now())).isTrue();
        assertThat(codigo.isUtilizavel(OffsetDateTime.now().plusMinutes(6))).isFalse();
        codigo.marcarUsado();
        assertThat(codigo.isUtilizavel(OffsetDateTime.now())).isFalse();
    }

    @Test
    void credenciaisTemPrefixoEHashEstavel() {
        String chave = CredenciaisIa.gerar(CredenciaisIa.PREFIXO_CHAVE);
        assertThat(chave).startsWith("ces_key_").hasSizeGreaterThan(40);
        assertThat(CredenciaisIa.gerar(CredenciaisIa.PREFIXO_CHAVE)).isNotEqualTo(chave);
        assertThat(CredenciaisIa.hash(chave)).isEqualTo(CredenciaisIa.hash(chave)).doesNotContain(chave);
        assertThat(CredenciaisIa.gerarClientId()).startsWith("ces-app-");
    }

    @Test
    void enderecoPublicoConfiguradoVenceARequisicao() {
        UrlPublicaIa urls = new UrlPublicaIa("https://app.exemplo.com.br/", "http://127.0.0.2:4211");
        assertThat(urls.mcp(new MockHttpServletRequest())).isEqualTo("https://app.exemplo.com.br/api/mcp");
    }

    @Test
    void enderecoPublicoLocalUsaOAppLiberadoNoCors() {
        UrlPublicaIa urls = new UrlPublicaIa("", "http://127.0.0.2:4211, https://app.exemplo.com.br");
        MockHttpServletRequest direto = new MockHttpServletRequest();
        direto.addHeader("Host", "127.0.0.1:8091");
        assertThat(urls.base(direto)).isEqualTo("http://127.0.0.2:4211");
        assertThat(urls.metadadosRecurso(direto))
                .isEqualTo("http://127.0.0.2:4211/.well-known/oauth-protected-resource/api/mcp");
    }

    @Test
    void enderecoPublicoForaDeLocalhostEhSempreHttps() {
        UrlPublicaIa urls = new UrlPublicaIa("", "https://app.exemplo.com.br");
        MockHttpServletRequest proxy = new MockHttpServletRequest();
        proxy.addHeader("Host", "interno:8080");
        proxy.addHeader("X-Forwarded-Host", "app.exemplo.com.br");
        proxy.addHeader("X-Forwarded-Proto", "http");
        assertThat(urls.base(proxy)).isEqualTo("https://app.exemplo.com.br");
    }
}
