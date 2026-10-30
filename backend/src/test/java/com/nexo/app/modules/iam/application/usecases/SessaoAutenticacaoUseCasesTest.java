package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.modules.iam.infrastructure.persistence.SessaoAutenticacaoRepository;
import com.nexo.app.shared.domain.exception.SessaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessaoAutenticacaoUseCasesTest {

    @Mock
    private SessaoAutenticacaoRepository repository;

    @InjectMocks
    private SessaoAutenticacaoUseCases useCases;

    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(useCases, "inatividadeDias", 30);
        ReflectionTestUtils.setField(useCases, "duracaoMaximaDias", 90);
    }

    private SessaoAutenticacao abrir(String[] tokenEmitido) {
        var captor = ArgumentCaptor.forClass(SessaoAutenticacao.class);
        when(repository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
        var emitida = useCases.iniciar(usuarioId, "Chrome Android");
        tokenEmitido[0] = emitida.refreshToken();
        var sessao = captor.getValue();
        lenient().when(repository.buscarParaRenovar(sessao.getId())).thenReturn(Optional.of(sessao));
        return sessao;
    }

    @Test
    @DisplayName("grava só o hash do segredo, nunca o token em claro")
    void naoGravaSegredoEmClaro() {
        var token = new String[1];
        var sessao = abrir(token);

        String segredo = token[0].substring(token[0].indexOf('.') + 1);
        assertThat(token[0]).startsWith(sessao.getId() + ".");
        assertThat(sessao.getHashTokenAtual()).isNotEqualTo(segredo).hasSize(64);
    }

    @Test
    @DisplayName("renovar gira o token: o novo funciona e o anterior vira retentativa")
    void renovarGiraToken() {
        var token = new String[1];
        var sessao = abrir(token);

        var renovada = useCases.renovar(token[0]);

        assertThat(renovada.refreshToken()).isNotEqualTo(token[0]);
        assertThat(sessao.apresentar(SessaoAutenticacaoUseCases.hash(segredo(renovada.refreshToken()))))
                .isEqualTo(SessaoAutenticacao.Apresentacao.ATUAL);
        assertThat(sessao.apresentar(SessaoAutenticacaoUseCases.hash(segredo(token[0]))))
                .isEqualTo(SessaoAutenticacao.Apresentacao.ANTERIOR);
    }

    @Test
    @DisplayName("resposta da renovação perdida no sinal fraco: o token anterior ainda renova")
    void retentativaComTokenAnteriorNaoDerrubaSessao() {
        var token = new String[1];
        var sessao = abrir(token);

        useCases.renovar(token[0]);           // resposta nunca chegou ao aparelho
        var retentativa = useCases.renovar(token[0]);

        assertThat(sessao.getRevogadaEm()).isNull();
        var seguinte = useCases.renovar(retentativa.refreshToken());
        assertThat(seguinte.refreshToken()).isNotBlank();
        assertThat(sessao.getRevogadaEm()).isNull();
    }

    @Test
    @DisplayName("token mais antigo que o anterior revoga a sessão (cópia do token)")
    void reusoDeTokenAntigoRevoga() {
        var token = new String[1];
        var sessao = abrir(token);

        var segunda = useCases.renovar(token[0]);
        useCases.renovar(segunda.refreshToken());

        assertThatThrownBy(() -> useCases.renovar(token[0]))
                .isInstanceOf(SessaoInvalidaException.class);
        assertThat(sessao.getRevogadaEm()).isNotNull();
        assertThat(sessao.getMotivoRevogacao()).isEqualTo(SessaoAutenticacao.MOTIVO_REUSO);
    }

    @Test
    @DisplayName("sessão expirada ou revogada não renova")
    void sessaoEncerradaNaoRenova() {
        var token = new String[1];
        var sessao = abrir(token);
        sessao.revogar(SessaoAutenticacao.MOTIVO_LOGOUT, OffsetDateTime.now());

        assertThatThrownBy(() -> useCases.renovar(token[0])).isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    @DisplayName("token malformado ou sem sessão é sessão inválida, não erro 500")
    void tokenMalformado() {
        assertThatThrownBy(() -> useCases.renovar("lixo")).isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> useCases.renovar(null)).isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> useCases.renovar(UUID.randomUUID() + ".x"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    @DisplayName("logout encerra a sessão do token apresentado")
    void logoutEncerra() {
        var token = new String[1];
        var sessao = abrir(token);
        when(repository.findById(sessao.getId())).thenReturn(Optional.of(sessao));

        useCases.encerrar(token[0]);

        assertThat(sessao.getMotivoRevogacao()).isEqualTo(SessaoAutenticacao.MOTIVO_LOGOUT);
    }

    @Test
    @DisplayName("revogar todas preserva a sessão do aparelho que pediu")
    void revogarTodasPreservaAtual() {
        var agora = OffsetDateTime.now();
        var atual = SessaoAutenticacao.iniciar(usuarioId, null, "a", agora, Duration.ofDays(30), Duration.ofDays(90));
        var outra = SessaoAutenticacao.iniciar(usuarioId, null, "b", agora, Duration.ofDays(30), Duration.ofDays(90));
        when(repository.buscarAtivasDoUsuario(usuarioId)).thenReturn(List.of(atual, outra));

        int revogadas = useCases.revogarTodas(usuarioId, atual.getId(), SessaoAutenticacao.MOTIVO_SENHA_ALTERADA);

        assertThat(revogadas).isEqualTo(1);
        assertThat(atual.getRevogadaEm()).isNull();
        assertThat(outra.getRevogadaEm()).isNotNull();
    }

    @Test
    @DisplayName("a renovação desliza a validade, mas nunca além do teto absoluto")
    void validadeDeslizaAteOTeto() {
        var inicio = OffsetDateTime.parse("2026-01-01T08:00:00-03:00");
        var sessao = SessaoAutenticacao.iniciar(usuarioId, null, "h", inicio, Duration.ofDays(30), Duration.ofDays(90));
        sessao.renovar(SessaoAutenticacao.Apresentacao.ATUAL, "h2", inicio.plusDays(25), Duration.ofDays(30));
        assertThat(sessao.getExpiraEm()).isEqualTo(inicio.plusDays(55));

        sessao.renovar(SessaoAutenticacao.Apresentacao.ATUAL, "h3", inicio.plusDays(50), Duration.ofDays(30));
        sessao.renovar(SessaoAutenticacao.Apresentacao.ATUAL, "h4", inicio.plusDays(75), Duration.ofDays(30));

        assertThat(sessao.getExpiraEm()).isEqualTo(inicio.plusDays(90));
        assertThat(sessao.isValida(inicio.plusDays(91))).isFalse();
        verify(repository, never()).save(any());
    }

    private static String segredo(String token) {
        return token.substring(token.indexOf('.') + 1);
    }
}
