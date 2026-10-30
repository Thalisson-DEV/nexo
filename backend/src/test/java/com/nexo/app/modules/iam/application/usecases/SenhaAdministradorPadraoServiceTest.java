package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SenhaAdministradorPadraoServiceTest {

    @Mock
    private UsuarioRepository repository;
    @Mock
    private SessaoAutenticacaoUseCases sessoes;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private SenhaAdministradorPadraoService servico(String senha) {
        return new SenhaAdministradorPadraoService(repository, encoder, sessoes, "USRADMIN", senha);
    }

    @Test
    void semSenhaConfiguradaNaoFazNada() {
        servico("").aplicar();
        servico(null).aplicar();
        verifyNoInteractions(repository, sessoes);
    }

    @Test
    void senhaCurtaEIgnorada() {
        servico("1234567").aplicar();
        verifyNoInteractions(repository, sessoes);
    }

    @Test
    void defineASenhaERevogaAsSessoesQuandoDifere() {
        Usuario admin = usuarioComSenha("antiga-senha");
        when(repository.findByLoginIgnoreCase("USRADMIN")).thenReturn(Optional.of(admin));

        servico("nova-senha-123").aplicar();

        verify(repository).save(admin);
        verify(sessoes).revogarTodas(any(), isNull(), eq(SessaoAutenticacao.MOTIVO_SENHA_ALTERADA));
        assertThat(encoder.matches("nova-senha-123", admin.getPassword())).isTrue();
    }

    @Test
    void reiniciarComAMesmaSenhaNaoDerrubaSessoes() {
        Usuario admin = usuarioComSenha("mesma-senha-123");
        when(repository.findByLoginIgnoreCase("USRADMIN")).thenReturn(Optional.of(admin));

        servico("mesma-senha-123").aplicar();

        verify(repository, never()).save(any());
        verifyNoInteractions(sessoes);
    }

    @Test
    void usuarioInexistenteSoAvisa() {
        when(repository.findByLoginIgnoreCase("USRADMIN")).thenReturn(Optional.empty());
        servico("qualquer-senha").aplicar();
        verify(repository, never()).save(any());
        verifyNoInteractions(sessoes);
    }

    private Usuario usuarioComSenha(String senha) {
        Usuario usuario = mock(Usuario.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(usuario, "senha", encoder.encode(senha));
        return usuario;
    }
}
