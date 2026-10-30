package com.nexo.app.shared.infrastructure.lookup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuração do limite fixo aplicado somente aos endpoints de lookup. */
@ConfigurationProperties(prefix = "ces.lookup.rate-limit")
public record LookupRateLimitProperties(
        boolean enabled,
        int requestsPerMinute,
        Duration window) {

    public LookupRateLimitProperties {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requests-per-minute deve ser positivo");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window deve ser uma duração positiva");
        }
    }

    public LookupRateLimitProperties() {
        this(true, 300, Duration.ofMinutes(1));
    }

    /** Janela em segundos, arredondada para cima para não expirar antes do configurado. */
    public long windowSeconds() {
        return Math.max(1, window.toSeconds() + (window.toNanosPart() == 0 ? 0 : 1));
    }
}
