package com.nexo.app.shared.infrastructure.lookup;

/** Exceção de transporte para uma janela de lookup excedida. */
public class LookupRateLimitExceededException extends RuntimeException {

    private final long retryAfter;

    public LookupRateLimitExceededException(long retryAfter) {
        super("Rate limit exceeded.");
        this.retryAfter = Math.max(1, retryAfter);
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}
