package com.nexo.app.modules.iam.infrastructure.security;

import com.nexo.app.shared.domain.exception.SessaoInvalidaException;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.infrastructure.security.AcessoExterno;
import com.nexo.app.shared.infrastructure.security.AcessoExternoResolver;
import com.nexo.app.shared.infrastructure.security.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UsuarioRepository repository;
    private final PerfilPermissaoCacheService perfilPermissaoCacheService;
    private final HandlerExceptionResolver exceptionResolver;
    private final ObjectProvider<AcessoExternoResolver> acessoExternoResolver;

    /** Única rota que aceita credencial de IA externa: uma chave vazada não abre o resto da API. */
    private static final String ROTA_ACESSO_EXTERNO = "/mcp";

    /**
     * Login, renovação e logout não leem o JWT. Um Authorization vencido que o aparelho
     * ainda envie nessas rotas faria o filtro responder 401 antes de o controller renovar
     * a sessão, que é exatamente o que o aparelho está tentando fazer.
     */
    private static final Set<String> ROTAS_DE_SESSAO = Set.of(
            "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/logout");

    /**
     * Technical probes are authenticated by their dedicated filter. Skipping
     * the user JWT filter is what keeps this route independent from JPA and
     * Redis, even when a stale Authorization header is also present.
     */
    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = caminho(request);
        return path.startsWith("/internal/observability/") || ROTAS_DE_SESSAO.contains(path);
    }

    private static String caminho(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return uri.substring(Math.min(contextPath.length(), uri.length()));
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
        try {
            var tokenJWT = recuperarToken(request);

            if (AcessoExterno.isCredencialExterna(tokenJWT)) {
                autenticarAcessoExterno(request, tokenJWT);
            } else if (tokenJWT != null) {
                var subject = tokenService.getSubject(tokenJWT);
                // Usuário removido ou desativado perde o acesso já na requisição seguinte,
                // sem esperar o JWT vencer.
                var usuario = repository.findById(UUID.fromString(subject))
                        .filter(Usuario::isAtivo)
                        .orElseThrow(() -> new SessaoInvalidaException("Usuário desativado. Procure o administrador."));

                // Busca as autoridades (ROLE + Permissões) do cache para evitar lazy loading em Virtual Threads
                var autoridadesStrings = perfilPermissaoCacheService.buscarAutoridadesPorPerfil(usuario.getPerfil().getId());
                var authorities = perfilPermissaoCacheService.converterParaAuthorities(autoridadesStrings);
                
                // Injeta no usuário para estar disponível via getAuthorities() se necessário
                usuario.setAuthorities(authorities);

                var authentication = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
                // A sessão do aparelho fica nos detalhes: trocar a senha preserva a deste aparelho.
                tokenService.getSessaoId(tokenJWT).ifPresent(authentication::setDetails);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            exceptionResolver.resolveException(request, response, null, ex);
        }
    }

    /**
     * Credencial de IA externa (MCP): só vale na rota do MCP. Credencial inválida não é erro aqui:
     * a requisição segue sem autenticação e a rota responde 401 com o caminho do OAuth.
     */
    private void autenticarAcessoExterno(HttpServletRequest request, String credencial) {
        AcessoExternoResolver resolver = acessoExternoResolver.getIfAvailable();
        if (resolver == null || !ROTA_ACESSO_EXTERNO.equals(caminho(request))) return;
        resolver.resolver(credencial, ipDe(request)).ifPresent(acesso -> repository.findById(acesso.usuarioId())
                .filter(Usuario::isAtivo)
                .ifPresent(usuario -> {
                    var authorities = perfilPermissaoCacheService.converterParaAuthorities(
                            perfilPermissaoCacheService.buscarAutoridadesPorPerfil(usuario.getPerfil().getId()));
                    usuario.setAuthorities(authorities);
                    var authentication = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
                    authentication.setDetails(acesso);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }));
    }

    private static String ipDe(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) return encaminhado.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String recuperarToken(HttpServletRequest request) {
        var authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null) {
            return authorizationHeader.replace("Bearer ", "");
        }
        return null;
    }
}
