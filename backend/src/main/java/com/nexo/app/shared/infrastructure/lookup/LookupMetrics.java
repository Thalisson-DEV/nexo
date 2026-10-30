package com.nexo.app.shared.infrastructure.lookup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Métricas comuns dos lookups. Os únicos tags aceitos são dimensões operacionais
 * de baixa cardinalidade; nunca inclua {@code q}, usuário ou cursor.
 */
@Component
public class LookupMetrics {

    private final MeterRegistry meterRegistry;

    public LookupMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry é obrigatório");
    }

    public void recordRequest(String resource, String endpoint, String status) {
        Counter.builder("lookup.request.count")
                .tags(tags(resource, endpoint, status))
                .register(meterRegistry)
                .increment();
    }

    public void recordQueryDuration(String resource, String endpoint, String status, Duration duration) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration deve ser não negativa");
        }
        Timer.builder("lookup.query.duration")
                .tags(tags(resource, endpoint, status))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordResultCount(String resource, String endpoint, String status, int resultCount) {
        if (resultCount < 0) {
            throw new IllegalArgumentException("resultCount não pode ser negativo");
        }
        Counter.builder("lookup.result.count")
                .tags(tags(resource, endpoint, status))
                .register(meterRegistry)
                .increment(resultCount);
    }

    public void recordCacheHit(String resource, String endpoint) {
        Counter.builder("lookup.cache.hit")
                .tags("resource", safeTag(resource), "endpoint", safeTag(endpoint))
                .register(meterRegistry)
                .increment();
    }

    public void recordCacheMiss(String resource, String endpoint) {
        Counter.builder("lookup.cache.miss")
                .tags("resource", safeTag(resource), "endpoint", safeTag(endpoint))
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimit(String resource, String endpoint) {
        Counter.builder("lookup.rate_limit")
                .tags("resource", safeTag(resource), "endpoint", safeTag(endpoint))
                .register(meterRegistry)
                .increment();
    }

    public <T> T observeQuery(String resource, String endpoint, String status, Supplier<T> query) {
        Objects.requireNonNull(query, "query é obrigatório");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return query.get();
        } finally {
            sample.stop(Timer.builder("lookup.query.duration")
                    .tags(tags(resource, endpoint, status))
                    .register(meterRegistry));
        }
    }

    private static String[] tags(String resource, String endpoint, String status) {
        return new String[]{
                "resource", safeTag(resource),
                "endpoint", safeTag(endpoint),
                "status", safeTag(status)
        };
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}
