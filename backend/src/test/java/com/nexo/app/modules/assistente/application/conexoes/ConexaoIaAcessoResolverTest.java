package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.domain.ConexaoIa;
import com.nexo.app.modules.assistente.domain.CredencialConexaoIa;
import com.nexo.app.modules.assistente.domain.TipoCredencialIa;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CredencialConexaoIaRepository;
import com.nexo.app.shared.infrastructure.security.AcessoExterno;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Qual credencial abre o MCP: so acesso ou chave validos, de conexao ainda conectada. */
class ConexaoIaAcessoResolverTest {

    private static final String CHAVE = "ces_key_teste";

    private final CredencialConexaoIaRepository credenciais = mock(CredencialConexaoIaRepository.class);
    private final ConexaoIaRepository conexoes = mock(ConexaoIaRepository.class);
    private final ConexaoIaAcessoResolver resolver = new ConexaoIaAcessoResolver(credenciais, conexoes);

    @Test
    void chaveValidaDeConexaoConectadaAbreComAPermissaoDaConexao() {
        ConexaoIa conexao = conexao(true);
        credencial(TipoCredencialIa.CHAVE, conexao, OffsetDateTime.now().plusDays(1));

        Optional<AcessoExterno> acesso = resolver.resolver(CHAVE, "10.0.0.1");

        assertThat(acesso).contains(new AcessoExterno(conexao.getUsuarioId(), conexao.getId(), true));
        verify(conexoes).registrarUso(eq(conexao.getId()), any(), eq("10.0.0.1"), any());
    }

    @Test
    void tokenDeRenovacaoNaoAbreOMcp() {
        credencial(TipoCredencialIa.RENOVACAO, conexao(false), OffsetDateTime.now().plusDays(1));
        assertThat(resolver.resolver(CHAVE, null)).isEmpty();
    }

    @Test
    void credencialVencidaNaoAbre() {
        credencial(TipoCredencialIa.ACESSO, conexao(false), OffsetDateTime.now().minusSeconds(1));
        assertThat(resolver.resolver(CHAVE, null)).isEmpty();
    }

    @Test
    void conexaoDesconectadaCortaNaHora() {
        ConexaoIa conexao = conexao(false);
        conexao.revogar(conexao.getUsuarioId());
        credencial(TipoCredencialIa.ACESSO, conexao, OffsetDateTime.now().plusHours(1));

        assertThat(resolver.resolver(CHAVE, null)).isEmpty();
        verify(conexoes, never()).registrarUso(any(), any(), any(), any());
    }

    @Test
    void credencialDesconhecidaOuVaziaNaoAbre() {
        when(credenciais.findByHash(any())).thenReturn(Optional.empty());
        assertThat(resolver.resolver("ces_key_outra", null)).isEmpty();
        assertThat(resolver.resolver("  ", null)).isEmpty();
        assertThat(resolver.resolver(null, null)).isEmpty();
    }

    @Test
    void ipMuitoLongoECortado() {
        ConexaoIa conexao = conexao(false);
        credencial(TipoCredencialIa.ACESSO, conexao, OffsetDateTime.now().plusHours(1));

        resolver.resolver(CHAVE, "x".repeat(200));

        verify(conexoes).registrarUso(eq(conexao.getId()), any(), eq("x".repeat(64)), any());
    }

    private ConexaoIa conexao(boolean escrita) {
        ConexaoIa conexao = ConexaoIa.porChave(UUID.randomUUID(), "Cursor", escrita, "ces_key_ab",
                OffsetDateTime.now().plusDays(30));
        ReflectionTestUtils.setField(conexao, "id", UUID.randomUUID());
        when(conexoes.findByIdAndAtivoTrue(conexao.getId())).thenReturn(Optional.of(conexao));
        return conexao;
    }

    private void credencial(TipoCredencialIa tipo, ConexaoIa conexao, OffsetDateTime expira) {
        CredencialConexaoIa credencial = new CredencialConexaoIa(conexao.getId(), tipo, CredenciaisIa.hash(CHAVE), expira);
        when(credenciais.findByHash(CredenciaisIa.hash(CHAVE))).thenReturn(Optional.of(credencial));
    }
}
