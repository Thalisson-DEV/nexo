package com.nexo.app.shared.infrastructure.lookup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupMetricsTest {

    @Test
    void registraMetricasOperacionaisComTagsDeBaixaCardinalidade() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LookupMetrics metrics = new LookupMetrics(registry);

        metrics.recordRequest("collaborator", "options", "200");
        metrics.recordCacheHit("collaborator", "options");
        metrics.recordCacheMiss("collaborator", "options");
        metrics.recordRateLimit("collaborator", "options");
        metrics.recordResultCount("collaborator", "options", "200", 3);
        metrics.recordQueryDuration("collaborator", "options", "200", Duration.ofMillis(4));

        assertThat(registry.get("lookup.request.count").tag("resource", "collaborator").counter().count()).isEqualTo(1);
        assertThat(registry.get("lookup.cache.hit").tag("endpoint", "options").counter().count()).isEqualTo(1);
        assertThat(registry.get("lookup.cache.miss").tag("endpoint", "options").counter().count()).isEqualTo(1);
        assertThat(registry.get("lookup.rate_limit").tag("resource", "collaborator").counter().count()).isEqualTo(1);
        assertThat(registry.get("lookup.result.count").tag("status", "200").counter().count()).isEqualTo(3);
        assertThat(registry.get("lookup.query.duration").tag("status", "200").timer().count()).isEqualTo(1);
        assertThat(registry.get("lookup.request.count").counter().getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("resource", "endpoint", "status");
    }

    @Test
    void observaConsultaMesmoQuandoElaFalha() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LookupMetrics metrics = new LookupMetrics(registry);

        assertThatThrownBy(() -> metrics.observeQuery("material", "options", "500", () -> {
            throw new IllegalStateException("falha");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("lookup.query.duration").tag("resource", "material").timer().count()).isEqualTo(1);
    }
}
