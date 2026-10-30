package com.nexo.app.shared.infrastructure.observability;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyModuleHealthProbeTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void emptyBaseIsSuccessfulWithoutCreatingFixtures() {
        var result = ReadOnlyModuleHealthProbe.execute(
                "estoque",
                ModuleHealthCheckContext.withTimeout(CLOCK, Duration.ofSeconds(2)),
                ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"),
                () -> { /* repository returned no row */ });

        assertThat(result.status()).isEqualTo(ModuleHealthStatus.UP);
        assertThat(result.reasonCode()).isEqualTo(ModuleHealthReason.CHECK_SUCCEEDED);
        assertThat(result.dependencies()).containsExactly("database");
    }

    @Test
    void isolatedReadFailureDoesNotExposeExceptionDetails() {
        var result = ReadOnlyModuleHealthProbe.execute(
                "contrato",
                ModuleHealthCheckContext.withTimeout(CLOCK, Duration.ofSeconds(2)),
                ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"),
                () -> { throw new IllegalStateException("connection string=secret"); });

        assertThat(result.status()).isEqualTo(ModuleHealthStatus.DOWN);
        assertThat(result.reasonCode()).isEqualTo(ModuleHealthReason.CHECK_FAILED);
        assertThat(result.toString()).doesNotContain("secret");
    }
}
