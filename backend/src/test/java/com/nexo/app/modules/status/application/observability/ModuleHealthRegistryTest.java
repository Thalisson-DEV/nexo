package com.nexo.app.modules.status.application.observability;

import com.nexo.app.shared.infrastructure.observability.ModuleHealthAdapter;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthCoverage;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthReason;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthResult;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthStatus;
import com.nexo.app.shared.infrastructure.observability.PassiveHealthMetrics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleHealthRegistryTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void keepsTheCanonicalFifteenModulesAndReportsMissingAdapters() {
        var registry = new ModuleHealthRegistry(List.of(adapter("iam")), CLOCK);

        assertThat(ModuleHealthRegistry.CANONICAL_MODULES).hasSize(15)
                .containsExactly("iam", "rh", "estoque", "contrato", "execucao", "dashboard",
                        "financeiro", "desconto", "obras", "sst", "chamado", "notificacao", "mencao",
                        "lookup", "assistente");
        assertThat(registry.hasCompleteCoverage()).isFalse();
        assertThat(registry.missingAdapters()).contains("rh", "assistente");
        assertThat(registry.check("rh").reasonCode()).isEqualTo(ModuleHealthReason.ADAPTER_MISSING);
        assertThatThrownBy(() -> registry.check("unknown"))
                .isInstanceOf(ModuleHealthRegistry.ModuleNotFoundException.class);
    }

    @Test
    void cachesAnEvidenceResultForFiveSecondsAndReturnsSanitizedDependencies() {
        var calls = new AtomicInteger();
        ModuleHealthAdapter adapter = new ModuleHealthAdapter() {
            @Override
            public String module() {
                return "iam";
            }

            @Override
            public ModuleHealthResult check(com.nexo.app.shared.infrastructure.observability.ModuleHealthCheckContext context) {
                calls.incrementAndGet();
                return new ModuleHealthResult("iam", ModuleHealthStatus.UP, NOW, 1,
                        ModuleHealthReason.CHECK_SUCCEEDED, ModuleHealthCoverage.FUNCTIONAL_READ,
                        List.of("database", "private-host.example", "bad dependency"));
            }
        };
        var registry = new ModuleHealthRegistry(List.of(adapter), CLOCK);

        ModuleHealthResult first = registry.check("iam");
        ModuleHealthResult second = registry.check("IAM");

        assertThat(first.status()).isEqualTo(ModuleHealthStatus.UP);
        assertThat(second).isEqualTo(first);
        assertThat(first.dependencies()).containsExactly("database", "private-host.example");
        assertThat(calls).hasValue(1);
    }

    @Test
    void promotesHealthySyntheticEvidenceToDegradedWhenPassiveErrorsAreSustained() {
        PassiveHealthMetrics metrics = new PassiveHealthMetrics(CLOCK);
        for (int i = 0; i < 19; i++) {
            metrics.recordSuccess("iam", "/v1/iam/usuarios");
        }
        metrics.recordTechnicalError("iam", "/v1/iam/usuarios");
        ModuleHealthRegistry registry = new ModuleHealthRegistry(List.of(adapter("iam")), CLOCK, metrics);

        ModuleHealthResult result = registry.check("iam");

        assertThat(result.status()).isEqualTo(ModuleHealthStatus.DEGRADED);
        assertThat(result.reasonCode()).isEqualTo(ModuleHealthReason.PASSIVE_TECHNICAL_ERRORS);
    }

    @Test
    void keepsProbeCapacityOccupiedUntilAnInterruptedAdapterActuallyReturns() throws Exception {
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        List<ModuleHealthAdapter> adapters = new ArrayList<>();
        for (String module : List.of("iam", "rh", "estoque", "contrato", "assistente")) {
            String key = module;
            adapters.add(new ModuleHealthAdapter() {
                @Override
                public String module() {
                    return key;
                }

                @Override
                public ModuleHealthResult check(com.nexo.app.shared.infrastructure.observability.ModuleHealthCheckContext context) {
                    entered.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await(100, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ignored) {
                            // Deliberately ignore cancellation to exercise permit ownership.
                        }
                    }
                    return adapter(key).check(context);
                }
            });
        }
        ModuleHealthRegistry registry = new ModuleHealthRegistry(adapters, CLOCK);
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Future<ModuleHealthResult>> running = new ArrayList<>();
            for (String module : ModuleHealthRegistry.CANONICAL_MODULES.subList(0, 4)) {
                running.add(callers.submit(() -> registry.check(module)));
            }
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            ModuleHealthResult fifth = registry.check("assistente");

            assertThat(fifth.reasonCode()).isEqualTo(ModuleHealthReason.PROBE_CAPACITY);
            Thread.sleep(2_200);
            release.countDown();
            for (Future<ModuleHealthResult> result : running) {
                assertThat(result.get(3, TimeUnit.SECONDS).reasonCode())
                        .isEqualTo(ModuleHealthReason.CHECK_TIMEOUT);
            }
        } finally {
            release.countDown();
            callers.shutdownNow();
            registry.shutdown();
        }
    }

    private static ModuleHealthAdapter adapter(String module) {
        return new ModuleHealthAdapter() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public ModuleHealthResult check(com.nexo.app.shared.infrastructure.observability.ModuleHealthCheckContext context) {
                return new ModuleHealthResult(module, ModuleHealthStatus.UP, NOW, 1,
                        ModuleHealthReason.CHECK_SUCCEEDED, ModuleHealthCoverage.FUNCTIONAL_READ, List.of());
            }
        };
    }
}
