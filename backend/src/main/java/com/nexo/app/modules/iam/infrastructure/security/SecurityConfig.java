package com.nexo.app.modules.iam.infrastructure.security;

import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.infrastructure.idempotencia.IdempotenciaService;
import com.nexo.app.shared.infrastructure.security.AcessoExternoResolver;
import com.nexo.app.shared.infrastructure.security.ObservabilityTechnicalTokenFilter;
import com.nexo.app.shared.infrastructure.security.ObservabilityTechnicalTokenProperties;
import com.nexo.app.shared.infrastructure.security.TokenService;
import com.nexo.app.shared.infrastructure.web.IdempotenciaFilter;
import com.nexo.app.shared.infrastructure.web.LookupRateLimitFilter;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;
    private final PerfilPermissaoCacheService perfilPermissaoCacheService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final IdempotenciaService idempotenciaService;
    private final LookupRateLimiter lookupRateLimiter;
    private final LookupMetrics lookupMetrics;
    private final ObservabilityTechnicalTokenProperties observabilityTokenProperties;
    private final ObjectProvider<AcessoExternoResolver> acessoExternoResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {
                    // 1. ROTAS PÚBLICAS (Login e Documentação)
                    req.requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll();
                    // Renovar e sair se autenticam pelo cookie da sessão, não pelo JWT, que
                    // pode já ter vencido justamente quando o aparelho precisa renová-lo.
                    req.requestMatchers(HttpMethod.POST, "/v1/auth/refresh", "/v1/auth/logout").permitAll();
                    req.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    req.requestMatchers("/ws/**").permitAll();
                    // Decisão financeira pelo link do e-mail: o token no caminho é a credencial
                    // e o serviço confere prazo, uso único, status e alçada do dono do link.
                    req.requestMatchers(HttpMethod.GET, "/v1/publico/financeiro/decisoes/*").permitAll();
                    req.requestMatchers(HttpMethod.POST, "/v1/publico/financeiro/decisoes/*").permitAll();

                    // Probes use a dedicated technical token. They never use the
                    // user JWT or the IAM repository/cache.
                    req.requestMatchers("/internal/observability/**")
                            .hasAuthority(ObservabilityTechnicalTokenFilter.AUTHORITY);

                    // Conexões de IA (MCP): a rota /mcp autentica a credencial da conexão e, sem ela,
                    // responde 401 apontando o OAuth. Descoberta, cadastro do aplicativo e troca de
                    // código por token são públicos por definição do protocolo (RFC 8414, 7591, 6749).
                    req.requestMatchers("/mcp", "/mcp/**").permitAll();
                    req.requestMatchers("/.well-known/**").permitAll();
                    req.requestMatchers(HttpMethod.POST, "/oauth/register", "/oauth/token", "/oauth/revoke").permitAll();

                    // Health check do Railway (context-path /api -> /api/actuator/health externamente)
                    req.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();

                    // 2. PADRÃO DE SEGURANÇA (Autenticação obrigatória para todo o resto)
                    // O controle granular (RBAC) é feito via @PreAuthorize nos UseCases/Controllers
                    req.anyRequest().authenticated();
                })
                .addFilterBefore(new ObservabilityTechnicalTokenFilter(observabilityTokenProperties), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SecurityFilter(tokenService, usuarioRepository, perfilPermissaoCacheService, handlerExceptionResolver, acessoExternoResolver), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new LookupRateLimitFilter(lookupRateLimiter, lookupMetrics, handlerExceptionResolver), SecurityFilter.class)
                // Depois da autorização de propósito: a chave de idempotência é escopada por
                // usuário, então reservá-la antes de saber quem pede permitiria a um anônimo
                // emperrar chaves alheias. Montado aqui, e não como @Component, porque o Boot
                // registraria um @Component na cadeia do servlet — antes da segurança, quando
                // o SecurityContext ainda está vazio.
                .addFilterAfter(new IdempotenciaFilter(idempotenciaService, handlerExceptionResolver), AuthorizationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
