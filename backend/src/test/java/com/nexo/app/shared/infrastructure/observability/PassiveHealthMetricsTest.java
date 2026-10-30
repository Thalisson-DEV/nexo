package com.nexo.app.shared.infrastructure.observability;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PassiveHealthMetricsTest {

    private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void countsTechnicalErrorsOnlyAfterMinimumSampleAndExcludesBusinessOutcomes() {
        MutableClock clock = new MutableClock(START);
        PassiveHealthMetrics metrics = new PassiveHealthMetrics(clock);

        for (int i = 0; i < 18; i++) {
            metrics.record("estoque", "/v1/materiais", PassiveHealthMetrics.Outcome.SUCCESS);
        }
        metrics.record("estoque", "/v1/materiais", PassiveHealthMetrics.Outcome.EXPECTED_CLIENT_ERROR);
        metrics.record("estoque", "/v1/materiais", PassiveHealthMetrics.Outcome.DOMAIN_ERROR);
        metrics.record("estoque", "/v1/materiais", PassiveHealthMetrics.Outcome.TECHNICAL_ERROR);

        assertThat(metrics.snapshot("estoque").operations()).isEqualTo(21);
        assertThat(metrics.snapshot("estoque").technicalErrors()).isEqualTo(1);
        assertThat(metrics.isDegraded("estoque")).isFalse();

        metrics.recordTechnicalError("estoque", "/v1/materiais");

        assertThat(metrics.snapshot("estoque").failureRate()).isGreaterThanOrEqualTo(0.05d);
        assertThat(metrics.isDegraded("estoque")).isTrue();
    }

    @Test
    void expiresOldEvidenceAndDoesNotKeepAStaleDegradedState() {
        MutableClock clock = new MutableClock(START);
        PassiveHealthMetrics metrics = new PassiveHealthMetrics(clock);
        for (int i = 0; i < 19; i++) {
            metrics.recordSuccess("rh", "/v1/rh/colaboradores");
        }
        metrics.recordTechnicalError("rh", "/v1/rh/colaboradores");

        assertThat(metrics.isDegraded("rh")).isTrue();

        clock.advanceSeconds(301);

        assertThat(metrics.snapshot("rh").operations()).isZero();
        assertThat(metrics.isDegraded("rh")).isFalse();
    }

    @Test
    void normalizesIdentifiersAndBoundsSeriesCardinality() {
        MutableClock clock = new MutableClock(START);
        PassiveHealthMetrics metrics = new PassiveHealthMetrics(clock);
        String first = PassiveHealthMetrics.normalizeRoute(
                "/v1/iam/usuarios/550e8400-e29b-41d4-a716-446655440000?usuario=private");
        String second = PassiveHealthMetrics.normalizeRoute(
                "/v1/iam/usuarios/123e4567-e89b-12d3-a456-426614174000");

        assertThat(first).isEqualTo("/v1/iam/usuarios/{id}");
        assertThat(second).isEqualTo(first);

        for (int i = 0; i < PassiveHealthMetrics.MAX_TRACKED_SERIES + 20; i++) {
            metrics.record("iam", "/v1/iam/usuarios/route-" + i, PassiveHealthMetrics.Outcome.SUCCESS);
        }

        assertThat(metrics.trackedSeriesCount()).isEqualTo(PassiveHealthMetrics.MAX_TRACKED_SERIES);
        assertThat(metrics.snapshot("iam", first).operations()).isZero();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        private void advanceSeconds(long seconds) {
            current.updateAndGet(value -> value.plusSeconds(seconds));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
