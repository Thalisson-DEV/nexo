package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.AlterarSenhaRequest;
import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlterarSenhaUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SessaoAutenticacaoUseCases sessoes;

    @Transactional
    public void executar(AlterarSenhaRequest request) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        String login = autenticacao.getName();
        
        Usuario usuario = repository.findByLoginIgnoreCase(login)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        if (!passwordEncoder.matches(request.senhaAntiga(), usuario.getSenha())) {
            throw new InvarianteVioladaException("A senha atual informada está incorreta");
        }

        if (passwordEncoder.matches(request.novaSenha(), usuario.getSenha())) {
            throw new InvarianteVioladaException("A nova senha deve ser diferente da senha atual");
        }

        String novaSenhaHash = passwordEncoder.encode(request.novaSenha());
        usuario.trocarSenha(novaSenhaHash);

        repository.save(usuario);

        // Outro aparelho com a senha antiga (perdido, emprestado) sai; este continua logado.
        UUID sessaoAtual = autenticacao.getDetails() instanceof UUID sid ? sid : null;
        sessoes.revogarTodas(usuario.getId(), sessaoAtual, SessaoAutenticacao.MOTIVO_SENHA_ALTERADA);
    }
}
