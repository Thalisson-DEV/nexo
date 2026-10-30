package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.application.dto.AutorizarIaRequest;
import com.nexo.app.modules.assistente.domain.ClienteConexaoIa;
import com.nexo.app.modules.assistente.domain.CodigoAutorizacaoIa;
import com.nexo.app.modules.assistente.domain.ConexaoIa;
import com.nexo.app.modules.assistente.domain.CredencialConexaoIa;
import com.nexo.app.modules.assistente.domain.TipoCredencialIa;
import com.nexo.app.modules.assistente.infrastructure.persistence.ClienteConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CodigoAutorizacaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CredencialConexaoIaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fluxo de token do OAuth das conexoes de IA, com os repositorios simulados. */
class OAuthIaServiceTest {

    private static final String RETORNO = "http://localhost:33418/callback";
    private static final String VERIFICADOR = "v".repeat(60);

    private final ClienteConexaoIaRepository clientes = mock(ClienteConexaoIaRepository.class);
    private final CodigoAutorizacaoIaRepository codigos = mock(CodigoAutorizacaoIaRepository.class);
    private final ConexaoIaRepository conexoes = mock(ConexaoIaRepository.class);
    private final CredencialConexaoIaRepository credenciais = mock(CredencialConexaoIaRepository.class);
    private final OAuthIaService servico = new OAuthIaService(clientes, codigos, conexoes, credenciais);
    private final List<CredencialConexaoIa> emitidas = new ArrayList<>();
    private ClienteConexaoIa cliente;

    @BeforeEach
    void preparar() {
        cliente = new ClienteConexaoIa("ces-app-teste", "Claude", List.of(RETORNO), null, "127.0.0.1");
        when(clientes.findByClientIdAndAtivoTrue("ces-app-teste")).thenReturn(Optional.of(cliente));
        when(conexoes.save(any(ConexaoIa.class))).thenAnswer(invocacao -> {
            ConexaoIa conexao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(conexao, "id", UUID.randomUUID());
            return conexao;
        });
        when(credenciais.save(any(CredencialConexaoIa.class))).thenAnswer(invocacao -> {
            emitidas.add(invocacao.getArgument(0));
            return invocacao.getArgument(0);
        });
    }

    @Test
    void codigoComPkceCertoViraTokensComOEscopoEscolhido() throws Exception {
        CodigoAutorizacaoIa codigo = codigo(true);
        when(codigos.findByHashForUpdate(anyString())).thenReturn(Optional.of(codigo));

        Map<String, Object> resposta = servico.token(troca(VERIFICADOR));

        assertThat((String) resposta.get("access_token")).startsWith("ces_at_");
        assertThat((String) resposta.get("refresh_token")).startsWith("ces_rt_");
        assertThat(resposta.get("scope")).isEqualTo("ces.leitura ces.escrita");
        assertThat(resposta.get("expires_in")).isEqualTo(3600L);
        assertThat(codigo.isUtilizavel(OffsetDateTime.now())).isFalse();
        assertThat(emitidas).extracting(CredencialConexaoIa::getTipo)
                .containsExactly(TipoCredencialIa.ACESSO, TipoCredencialIa.RENOVACAO);
        assertThat(emitidas).allSatisfy(c -> assertThat(c.getHash()).hasSize(64));
    }

    @Test
    void verificadorErradoRecusaEQueimaOCodigo() throws Exception {
        CodigoAutorizacaoIa codigo = codigo(false);
        when(codigos.findByHashForUpdate(anyString())).thenReturn(Optional.of(codigo));

        assertThatThrownBy(() -> servico.token(troca("x".repeat(60))))
                .isInstanceOf(OAuthErro.class).extracting("codigo").isEqualTo("invalid_grant");
        assertThat(codigo.isUtilizavel(OffsetDateTime.now())).isFalse();
        assertThat(emitidas).isEmpty();
    }

    @Test
    void retornoDiferenteDoDaAutorizacaoERecusado() throws Exception {
        when(codigos.findByHashForUpdate(anyString())).thenReturn(Optional.of(codigo(false)));
        Map<String, String> pedido = new java.util.HashMap<>(troca(VERIFICADOR));
        pedido.put("redirect_uri", "http://localhost:1/outro");

        assertThatThrownBy(() -> servico.token(pedido)).isInstanceOf(OAuthErro.class);
        assertThat(emitidas).isEmpty();
    }

    @Test
    void renovacaoTrocaOsTokensEInvalidaOAnterior() {
        ConexaoIa conexao = conexao();
        CredencialConexaoIa renovacao = renovacao(conexao, null);
        when(credenciais.findByHashForUpdate(anyString())).thenReturn(Optional.of(renovacao));
        when(conexoes.findByIdAndAtivoTrue(conexao.getId())).thenReturn(Optional.of(conexao));

        Map<String, Object> resposta = servico.token(Map.of("grant_type", "refresh_token", "refresh_token", "ces_rt_x"));

        assertThat(resposta).containsKeys("access_token", "refresh_token");
        assertThat(resposta.get("scope")).isEqualTo("ces.leitura");
        assertThat(renovacao.getUsadaEm()).isNotNull();
    }

    @Test
    void reusoDeRenovacaoAntigaDesconectaAConexao() {
        ConexaoIa conexao = conexao();
        CredencialConexaoIa renovacao = renovacao(conexao, OffsetDateTime.now().minusMinutes(10));
        when(credenciais.findByHashForUpdate(anyString())).thenReturn(Optional.of(renovacao));
        when(conexoes.findForUpdate(conexao.getId())).thenReturn(Optional.of(conexao));

        assertThatThrownBy(() -> servico.token(Map.of("grant_type", "refresh_token", "refresh_token", "ces_rt_x")))
                .isInstanceOf(OAuthErro.class);
        assertThat(conexao.isRevogada()).isTrue();
    }

    @Test
    void renovacaoRepetidaEmParaleloSoRecusa() {
        ConexaoIa conexao = conexao();
        CredencialConexaoIa renovacao = renovacao(conexao, OffsetDateTime.now().minusSeconds(5));
        when(credenciais.findByHashForUpdate(anyString())).thenReturn(Optional.of(renovacao));
        when(conexoes.findForUpdate(conexao.getId())).thenReturn(Optional.of(conexao));

        assertThatThrownBy(() -> servico.token(Map.of("grant_type", "refresh_token", "refresh_token", "ces_rt_x")))
                .isInstanceOf(OAuthErro.class);
        assertThat(conexao.isRevogada()).isFalse();
    }

    @Test
    void conexaoDesconectadaNaoRenova() {
        ConexaoIa conexao = conexao();
        conexao.revogar(conexao.getUsuarioId());
        when(credenciais.findByHashForUpdate(anyString())).thenReturn(Optional.of(renovacao(conexao, null)));
        when(conexoes.findByIdAndAtivoTrue(conexao.getId())).thenReturn(Optional.of(conexao));

        assertThatThrownBy(() -> servico.token(Map.of("grant_type", "refresh_token", "refresh_token", "ces_rt_x")))
                .isInstanceOf(OAuthErro.class).hasMessageContaining("desconectada");
        assertThat(emitidas).isEmpty();
    }

    @Test
    void tipoDeConcessaoDesconhecidoERecusado() {
        assertThatThrownBy(() -> servico.token(Map.of("grant_type", "password")))
                .isInstanceOf(OAuthErro.class).extracting("codigo").isEqualTo("unsupported_grant_type");
    }

    @Test
    void recusaDevolveAccessDeniedComStateEEmissor() {
        AutorizarIaRequest pedido = new AutorizarIaRequest("code", "ces-app-teste", RETORNO, "desafio", "S256", "abc",
                "ces.leitura", null, false);

        var params = UriComponentsBuilder.fromUriString(servico.recusar(pedido, "https://app.exemplo.com.br").redirectUrl())
                .build().getQueryParams();

        assertThat(params.getFirst("error")).isEqualTo("access_denied");
        assertThat(params.getFirst("state")).isEqualTo("abc");
        assertThat(params.getFirst("iss")).isEqualTo("https://app.exemplo.com.br");
    }

    @Test
    void pedidoSemPkceOuComRetornoNaoCadastradoERecusado() {
        AutorizarIaRequest semPkce = new AutorizarIaRequest("code", "ces-app-teste", RETORNO, null, null, null, null, null, false);
        AutorizarIaRequest outroRetorno = new AutorizarIaRequest("code", "ces-app-teste", "http://localhost:9/x", "d", "S256",
                null, null, null, false);
        AutorizarIaRequest plain = new AutorizarIaRequest("code", "ces-app-teste", RETORNO, "d", "plain", null, null, null, false);

        assertThatThrownBy(() -> servico.previa(semPkce, true)).isInstanceOf(OAuthErro.class);
        assertThatThrownBy(() -> servico.previa(outroRetorno, true)).isInstanceOf(OAuthErro.class);
        assertThatThrownBy(() -> servico.previa(plain, true)).isInstanceOf(OAuthErro.class);
    }

    @Test
    void previaSoOfereceEscritaSeOPerfilPodeEOEscopoPede() {
        AutorizarIaRequest leitura = new AutorizarIaRequest("code", "ces-app-teste", RETORNO, "d", "S256", null, "ces.leitura",
                null, false);
        AutorizarIaRequest ambos = new AutorizarIaRequest("code", "ces-app-teste", RETORNO, "d", "S256", null,
                "ces.leitura ces.escrita", null, false);

        assertThat(servico.previa(leitura, true).podeEscrever()).isFalse();
        assertThat(servico.previa(ambos, false).podeEscrever()).isFalse();
        assertThat(servico.previa(ambos, true).podeEscrever()).isTrue();
        assertThat(servico.previa(ambos, true).destino()).isEqualTo("localhost");
        assertThat(servico.previa(ambos, true).aplicativo()).isEqualTo("Claude");
    }

    private CodigoAutorizacaoIa codigo(boolean escrita) throws Exception {
        String desafio = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFICADOR.getBytes(StandardCharsets.US_ASCII)));
        return new CodigoAutorizacaoIa("hash", "ces-app-teste", UUID.randomUUID(), escrita, RETORNO, desafio, null,
                OffsetDateTime.now().plusMinutes(5));
    }

    private static Map<String, String> troca(String verificador) {
        return Map.of("grant_type", "authorization_code", "code", "ces_code_x", "redirect_uri", RETORNO,
                "client_id", "ces-app-teste", "code_verifier", verificador);
    }

    private ConexaoIa conexao() {
        ConexaoIa conexao = ConexaoIa.porLogin(UUID.randomUUID(), cliente, false);
        ReflectionTestUtils.setField(conexao, "id", UUID.randomUUID());
        return conexao;
    }

    private static CredencialConexaoIa renovacao(ConexaoIa conexao, OffsetDateTime usadaEm) {
        CredencialConexaoIa credencial = new CredencialConexaoIa(conexao.getId(), TipoCredencialIa.RENOVACAO, "h".repeat(64),
                OffsetDateTime.now().plusDays(30));
        ReflectionTestUtils.setField(credencial, "usadaEm", usadaEm);
        return credencial;
    }
}
