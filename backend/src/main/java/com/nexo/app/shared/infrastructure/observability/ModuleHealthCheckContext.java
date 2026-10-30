package com.nexo.app.shared.infrastructure.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable execution budget supplied to a module health adapter. */
public record ModuleHealthCheckContext(Instant deadline, Clock clock) {

    public ModuleHealthCheckContext {
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(clock, "clock");
    }

    public static ModuleHealthCheckContext withTimeout(Clock clock, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout deve ser positivo");
        }
        return new ModuleHealthCheckContext(Instant.now(clock).plus(timeout), clock);
    }

    public boolean expired() {
        return !Instant.now(clock).isBefore(deadline);
    }

    public Duration remaining() {
        Duration remaining = Duration.between(Instant.now(clock), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
