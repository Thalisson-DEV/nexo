package com.nexo.app.shared.infrastructure.web;

import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimitDecision;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimitExceededException;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimiter;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.UUID;

/** Aplica limite somente aos GET de autocomplete e bootstrap de lookup. */
@RequiredArgsConstructor
public class LookupRateLimitFilter extends OncePerRequestFilter {

    private static final String RESOURCE = "lookup";

    private final LookupRateLimiter limiter;
    private final LookupMetrics metrics;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.endsWith("/options") || path.endsWith("/lookups/bootstrap"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String userKey = userKey();
        // A autorização da cadeia continua sendo a autoridade final. Antes dela,
        // sem identidade autenticada, não existe chave de usuário para consumir.
        if (userKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        LookupRateLimitDecision decision = limiter.tryAcquire(userKey);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        String endpoint = request.getRequestURI().endsWith("/lookups/bootstrap")
                ? "bootstrap" : "options";
        metrics.recordRateLimit(RESOURCE, endpoint);
        exceptionResolver.resolveException(request, response, null,
                new LookupRateLimitExceededException(decision.retryAfterSeconds()));
    }

    private static String userKey() {
        UUID userId = SecurityUtils.getUsuarioLogadoId();
        if (userId != null) {
            return userId.toString();
        }
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }
}
