package com.nexo.app.shared.infrastructure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityTechnicalTokenFilterTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInternalRequestsWithoutTechnicalTokenEvenWhenAuthorizationHasUserToken() throws Exception {
        var filter = new ObservabilityTechnicalTokenFilter(
                new ObservabilityTechnicalTokenProperties("probe-secret"));
        var request = request("/api/internal/observability/modules/iam");
        request.addHeader("Authorization", "Bearer user-jwt");
        var response = new MockHttpServletResponse();
        var called = new boolean[1];

        filter.doFilter(request, response, chain(called));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(called[0]).isFalse();
    }

    @Test
    void authenticatesOnlyThePrivateRouteWithTheDedicatedHeader() throws Exception {
        var filter = new ObservabilityTechnicalTokenFilter(
                new ObservabilityTechnicalTokenProperties("probe-secret"));
        var request = request("/api/internal/observability/modules/iam");
        request.addHeader(ObservabilityTechnicalTokenFilter.HEADER, "probe-secret");
        var response = new MockHttpServletResponse();
        var called = new boolean[1];

        filter.doFilter(request, response, chain(called));

        assertThat(called[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly(ObservabilityTechnicalTokenFilter.AUTHORITY);
    }

    @Test
    void doesNotTurnTechnicalHeaderIntoBusinessAuthentication() throws Exception {
        var filter = new ObservabilityTechnicalTokenFilter(
                new ObservabilityTechnicalTokenProperties("probe-secret"));
        var request = request("/api/v1/estoque/materiais");
        request.addHeader(ObservabilityTechnicalTokenFilter.HEADER, "probe-secret");
        var response = new MockHttpServletResponse();
        var called = new boolean[1];

        filter.doFilter(request, response, chain(called));

        assertThat(called[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String uri) {
        var request = new MockHttpServletRequest();
        request.setContextPath("/api");
        request.setRequestURI(uri);
        return request;
    }

    private static FilterChain chain(boolean[] called) {
        return (request, response) -> called[0] = true;
    }
}
