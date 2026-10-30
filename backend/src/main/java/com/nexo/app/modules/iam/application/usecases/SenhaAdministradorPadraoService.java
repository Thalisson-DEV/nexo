package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Define na subida a senha do administrador padrão (criado pela V20) a partir do ambiente, para que um banco
 * de desenvolvimento ou homologação nunca fique sem acesso por senha esquecida.
 *
 * <p>Só age com {@code ADMIN_PADRAO_SENHA} preenchida; vazia, não faz nada (padrão em produção). Se a senha
 * atual já confere, não altera nada, então reiniciar não derruba as sessões abertas. Ao trocar, revoga as
 * sessões do usuário, como na redefinição feita pela tela.</p>
 */
@Slf4j
@Service
public class SenhaAdministradorPadraoService {

    static final int TAMANHO_MINIMO = 8;

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SessaoAutenticacaoUseCases sessoes;
    private final String login;
    private final String senha;

    public SenhaAdministradorPadraoService(UsuarioRepository repository,
                                           PasswordEncoder passwordEncoder,
                                           SessaoAutenticacaoUseCases sessoes,
                                           @Value("${ces.security.admin-padrao.login:USRADMIN}") String login,
                                           @Value("${ces.security.admin-padrao.senha:}") String senha) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessoes = sessoes;
        this.login = login;
        this.senha = senha;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void aplicar() {
        if (senha == null || senha.isBlank()) {
            return;
        }
        if (senha.length() < TAMANHO_MINIMO) {
            log.warn("ADMIN_PADRAO_SENHA ignorada: use ao menos {} caracteres.", TAMANHO_MINIMO);
            return;
        }
        Optional<Usuario> encontrado = repository.findByLoginIgnoreCase(login);
        if (encontrado.isEmpty()) {
            log.warn("ADMIN_PADRAO_SENHA ignorada: usuário {} não encontrado.", login);
            return;
        }
        Usuario usuario = encontrado.get();
        if (passwordEncoder.matches(senha, usuario.getPassword())) {
            return;
        }
        usuario.trocarSenha(passwordEncoder.encode(senha));
        repository.save(usuario);
        sessoes.revogarTodas(usuario.getId(), null, SessaoAutenticacao.MOTIVO_SENHA_ALTERADA);
        if (!usuario.isAtivo()) {
            log.warn("Senha do usuário {} definida pelo ambiente, mas o usuário está inativo.", login);
        } else {
            log.info("Senha do usuário {} definida pelo ambiente (ADMIN_PADRAO_SENHA).", login);
        }
    }
}
