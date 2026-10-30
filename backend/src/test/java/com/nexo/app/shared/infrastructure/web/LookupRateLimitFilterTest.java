package com.nexo.app.shared.infrastructure.web;

import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimitDecision;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtraSomenteGetDeOptionsEBootstrapEUsaIdentidadeDoContexto() throws Exception {
        LookupRateLimiter limiter = mock(LookupRateLimiter.class);
        LookupMetrics metrics = mock(LookupMetrics.class);
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(limiter.tryAcquire(any())).thenReturn(LookupRateLimitDecision.allow());
        LookupRateLimitFilter filter = new LookupRateLimitFilter(limiter, metrics, resolver);
        String userId = UUID.randomUUID().toString();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rh/colaboradores/options");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(limiter).tryAcquire(userId);
        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void aoExcederNaoChamaControllerResolveExcecaoEMedeEventoSemDadosSensveis() throws Exception {
        LookupRateLimiter limiter = mock(LookupRateLimiter.class);
        LookupMetrics metrics = mock(LookupMetrics.class);
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(limiter.tryAcquire(any())).thenReturn(LookupRateLimitDecision.reject(7));
        LookupRateLimitFilter filter = new LookupRateLimitFilter(limiter, metrics, resolver);
        String userId = UUID.randomUUID().toString();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/lookups/bootstrap");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolver).resolveException(any(), any(), any(), any());
        verify(metrics).recordRateLimit("lookup", "bootstrap");
        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNull();
    }

    @Test
    void ignoraMetodosEPadroesForaDoLookup() throws Exception {
        LookupRateLimiter limiter = mock(LookupRateLimiter.class);
        LookupRateLimitFilter filter = new LookupRateLimitFilter(limiter, mock(LookupMetrics.class),
                mock(HandlerExceptionResolver.class));

        MockFilterChain postChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/rh/colaboradores/options"),
                new MockHttpServletResponse(), postChain);
        MockFilterChain otherChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/rh/colaboradores"),
                new MockHttpServletResponse(), otherChain);

        verify(limiter, never()).tryAcquire(any());
        org.assertj.core.api.Assertions.assertThat(postChain.getRequest()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(otherChain.getRequest()).isNotNull();
    }
}
