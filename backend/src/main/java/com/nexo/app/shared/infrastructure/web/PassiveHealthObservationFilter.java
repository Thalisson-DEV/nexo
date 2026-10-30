package com.nexo.app.shared.infrastructure.web;

import com.nexo.app.shared.domain.exception.BaseBusinessException;
import com.nexo.app.shared.infrastructure.observability.PassiveHealthMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Records HTTP outcomes locally for passive module health evidence. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PassiveHealthObservationFilter extends OncePerRequestFilter {

    private static final Set<String> MODULES = Set.of(
            "iam", "rh", "estoque", "contrato", "execucao", "dashboard",
            "financeiro", "desconto", "obras", "sst", "chamado", "notificacao",
            "mencao", "lookup", "assistente");
    private final PassiveHealthMetrics metrics;

    public PassiveHealthObservationFilter(PassiveHealthMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return !path.startsWith("/v1/") && !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            String module = moduleFromHandler(request);
            if (module == null) {
                module = moduleFromPath(request);
            }
            if (module != null) {
                if (failure != null && !isExpectedBusinessFailure(failure)
                        && response.getStatus() < 500) {
                    metrics.record(module,
                            PassiveHealthMetrics.routeKey(request.getMethod(), request.getRequestURI()),
                            PassiveHealthMetrics.Outcome.TECHNICAL_ERROR);
                } else {
                    metrics.recordHttpOutcome(module, request.getMethod(), request.getRequestURI(), response.getStatus());
                }
            }
        }
    }

    private static boolean isExpectedBusinessFailure(Throwable failure) {
        return failure instanceof BaseBusinessException
                || failure instanceof AuthorizationDeniedException;
    }

    private static String moduleFromHandler(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod method)) {
            return null;
        }
        String name = method.getBeanType().getPackageName();
        String marker = ".modules.";
        int start = name.indexOf(marker);
        if (start < 0) {
            return null;
        }
        String module = name.substring(start + marker.length()).split("\\.", 2)[0]
                .toLowerCase(Locale.ROOT);
        return MODULES.contains(module) ? module : null;
    }

    private static String moduleFromPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].toLowerCase(Locale.ROOT);
            if (MODULES.contains(segment)) {
                return segment;
            }
        }
        if (path.contains("/auth/")) {
            return "iam";
        }
        return null;
    }
}
