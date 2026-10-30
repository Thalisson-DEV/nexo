package com.nexo.app.shared.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secret used only by private observability probes, never by business APIs. */
@ConfigurationProperties(prefix = "ces.observability")
public record ObservabilityTechnicalTokenProperties(String technicalToken) {
    public boolean configured() {
        return technicalToken != null && !technicalToken.isBlank();
    }
}
