package com.nexo.app.shared.infrastructure.lookup;

/** Resultado de uma tentativa de consumo da janela de rate limit. */
public record LookupRateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public LookupRateLimitDecision {
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException("retryAfterSeconds não pode ser negativo");
        }
    }

    public static LookupRateLimitDecision allow() {
        return new LookupRateLimitDecision(true, 0);
    }

    public static LookupRateLimitDecision reject(long retryAfterSeconds) {
        return new LookupRateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}
