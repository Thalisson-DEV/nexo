package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.iam.application.dto.LoginRequest;
import com.nexo.app.modules.iam.application.usecases.SessaoAutenticacaoUseCases.SessaoEmitida;
import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.shared.domain.exception.SessaoInvalidaException;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.iam.infrastructure.security.PerfilPermissaoCacheService;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.infrastructure.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Emite o par de credenciais do aparelho: o token de acesso (JWT curto, enviado em cada
 * requisição) e o refresh token (longo, rotativo, só trafega no cookie HttpOnly).
 *
 * <p>A renovação remonta o JWT a partir do banco, e não copia as claims do token antigo:
 * permissões, equipe e estoque alterados pelo administrador chegam ao aparelho na próxima
 * renovação, sem exigir novo login.</p>
 */
@Service
@RequiredArgsConstructor
public class AutenticarUseCase {

    private final AuthenticationManager manager;
    private final TokenService tokenService;
    private final SessaoAutenticacaoUseCases sessoes;
    private final UsuarioRepository usuarioRepository;
    private final PerfilPermissaoCacheService perfilPermissaoCacheService;
    private final ColaboradorApi colaboradorApi;
    private final EquipeApi equipeApi;
    private final EstoqueApi estoqueApi;

    /** Credenciais emitidas; o refresh token vai para o cookie, nunca para o corpo. */
    public record Credenciais(String tokenAcesso, String refreshToken, OffsetDateTime sessaoExpiraEm) {}

    public Credenciais login(LoginRequest request, String dispositivo) {
        var autenticacao = manager.authenticate(
                new UsernamePasswordAuthenticationToken(request.login(), request.senha()));
        Usuario usuario = (Usuario) autenticacao.getPrincipal();
        if (usuario == null) {
            throw new IllegalStateException("Autenticação sem usuário associado");
        }

        var roles = usuario.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        SessaoEmitida sessao = sessoes.iniciar(usuario.getId(), dispositivo);
        return credenciais(usuario, roles, sessao);
    }

    public Credenciais renovar(String refreshToken) {
        SessaoEmitida sessao = sessoes.renovar(refreshToken);

        Usuario usuario = usuarioRepository.findById(sessao.usuarioId())
                .filter(Usuario::isAtivo)
                .orElse(null);
        if (usuario == null) {
            sessoes.revogar(sessao.sessaoId(), SessaoAutenticacao.MOTIVO_USUARIO_INATIVO);
            throw new SessaoInvalidaException("Usuário desativado. Procure o administrador.");
        }

        var autoridades = perfilPermissaoCacheService.buscarAutoridadesPorPerfil(usuario.getPerfil().getId());
        var roles = perfilPermissaoCacheService.converterParaAuthorities(autoridades).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return credenciais(usuario, roles, sessao);
    }

    private Credenciais credenciais(Usuario usuario, List<String> roles, SessaoEmitida sessao) {
        String nome = colaboradorApi.buscarNomeCompletoPorUsuario(usuario.getId()).orElse(usuario.getLogin());
        UUID equipeId = equipeApi.buscarIdEquipePorUsuario(usuario.getId()).orElse(null);
        UUID estoqueId = equipeId != null ? estoqueApi.buscarIdEstoquePorEquipe(equipeId).orElse(null) : null;
        String tipoUsuario = usuario.getTipo() != null ? usuario.getTipo().name() : null;

        String tokenAcesso = tokenService.gerarToken(
                usuario.getId(), nome, equipeId, estoqueId, roles, tipoUsuario, sessao.sessaoId());
        return new Credenciais(tokenAcesso, sessao.refreshToken(), sessao.expiraEm());
    }
}
