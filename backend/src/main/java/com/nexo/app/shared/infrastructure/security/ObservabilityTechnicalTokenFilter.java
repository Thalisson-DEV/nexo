package com.nexo.app.shared.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates only private observability paths without touching IAM, JPA or
 * Redis. The dedicated header prevents a user JWT from being accepted as a
 * monitoring credential.
 */
public final class ObservabilityTechnicalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Observability-Token";
    public static final String AUTHORITY = "OBSERVABILITY_READ";
    private static final String ROUTE_PREFIX = "/internal/observability/";

    private final ObservabilityTechnicalTokenProperties properties;

    public ObservabilityTechnicalTokenFilter(ObservabilityTechnicalTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.substring(Math.min(contextPath.length(), uri.length()));
        return !path.startsWith(ROUTE_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (!matches(presented)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "observability-probe", null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean matches(String presented) {
        if (!properties.configured() || presented == null) {
            return false;
        }
        byte[] expected = properties.technicalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = presented.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
