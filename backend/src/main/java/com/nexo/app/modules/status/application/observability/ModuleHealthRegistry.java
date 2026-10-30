package com.nexo.app.modules.status.application.observability;

import com.nexo.app.shared.infrastructure.observability.ModuleHealthAdapter;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthCheckContext;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthReason;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthResult;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthStatus;
import com.nexo.app.shared.infrastructure.observability.PassiveHealthMetrics;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Registry and execution boundary for module health probes.
 *
 * <p>Only this class knows the canonical inventory. Module adapters are
 * discovered through the shared SPI, keeping Spring Modulith boundaries intact.
 * A missing adapter is returned as UNKNOWN and is also exposed through
 * {@link #missingAdapters()} so catalog validation can block activation.</p>
 */
@Component
public class ModuleHealthRegistry {

    public static final List<String> CANONICAL_MODULES = List.of(
            "iam", "rh", "estoque", "contrato", "execucao", "dashboard",
            "financeiro", "desconto", "obras", "sst", "chamado", "notificacao",
            "mencao", "lookup", "assistente");

    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(2);
    static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final int MAX_CONCURRENT_PROBES = 4;
    private static final Pattern SAFE_DEPENDENCY = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,63}");

    private final Map<String, ModuleHealthAdapter> adapters;
    private final PassiveHealthMetrics passiveHealthMetrics;
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_PROBES);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ModuleHealthRegistry(List<ModuleHealthAdapter> adapters) {
        this(adapters, Clock.systemUTC(), new PassiveHealthMetrics(Clock.systemUTC()));
    }

    ModuleHealthRegistry(List<ModuleHealthAdapter> adapters, Clock clock) {
        this(adapters, clock, new PassiveHealthMetrics(clock));
    }

    @Autowired
    public ModuleHealthRegistry(List<ModuleHealthAdapter> adapters, PassiveHealthMetrics passiveHealthMetrics) {
        this(adapters, Clock.systemUTC(), passiveHealthMetrics);
    }

    ModuleHealthRegistry(List<ModuleHealthAdapter> adapters, Clock clock,
                         PassiveHealthMetrics passiveHealthMetrics) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.passiveHealthMetrics = Objects.requireNonNull(passiveHealthMetrics, "passiveHealthMetrics");
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                adapter -> canonicalize(adapter.module()),
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Mais de um adapter para o módulo");
                }));
    }

    public ModuleHealthResult check(String module) {
        String key = canonicalize(module);
        if (!CANONICAL_MODULES.contains(key)) {
            throw new ModuleNotFoundException(key);
        }

        CachedResult cached = cache.get(key);
        Instant now = Instant.now(clock);
        if (cached != null && cached.checkedAt().plus(CACHE_TTL).isAfter(now)) {
            // A passive degradation is derived from a five-minute window. Do
            // not keep returning it from the evidence cache after that window
            // has expired; the next adapter read can establish the new state.
            if (cached.result().reasonCode() != ModuleHealthReason.PASSIVE_TECHNICAL_ERRORS
                    || passiveHealthMetrics.isDegraded(key)) {
                return cached.result();
            }
        }

        ModuleHealthAdapter adapter = adapters.get(key);
        if (adapter == null) {
            return ModuleHealthResult.unknown(key, now, ModuleHealthReason.ADAPTER_MISSING, List.of());
        }
        if (!permits.tryAcquire()) {
            return ModuleHealthResult.unknown(key, now, ModuleHealthReason.PROBE_CAPACITY, List.of());
        }

        Instant started = Instant.now(clock);
        java.util.concurrent.Future<ModuleHealthResult> future = null;
        ProbePermit permit = new ProbePermit(permits);
        try {
            future = executor.submit(() -> executeWithPermit(adapter, permit));
            ModuleHealthResult result = future.get(CHECK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            ModuleHealthResult sanitized = sanitize(key, result, started);
            sanitized = applyPassiveEvidence(key, sanitized);
            cache.put(key, new CachedResult(sanitized, sanitized.checkedAt()));
            return sanitized;
        } catch (TimeoutException exception) {
            return ModuleHealthResult.unknown(key, Instant.now(clock), ModuleHealthReason.CHECK_TIMEOUT,
                    List.of());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ModuleHealthResult.unknown(key, Instant.now(clock), ModuleHealthReason.CHECK_TIMEOUT,
                    List.of());
        } catch (Exception exception) {
            return ModuleHealthResult.unknown(key, Instant.now(clock), ModuleHealthReason.CHECK_FAILED,
                    List.of());
        } finally {
            if (future != null && future.cancel(true)) {
                permit.cancelBeforeStart();
            } else if (future == null) {
                permit.release();
            }
        }
    }

    private ModuleHealthResult executeWithPermit(ModuleHealthAdapter adapter, ProbePermit permit) {
        if (!permit.start()) {
            return ModuleHealthResult.unknown(adapter.module(), Instant.now(clock),
                    ModuleHealthReason.CHECK_TIMEOUT, List.of());
        }
        try {
            return adapter.check(ModuleHealthCheckContext.withTimeout(clock, CHECK_TIMEOUT));
        } finally {
            permit.release();
        }
    }

    private ModuleHealthResult applyPassiveEvidence(String module, ModuleHealthResult result) {
        if (result.status() == ModuleHealthStatus.UP
                && passiveHealthMetrics.isDegraded(module)) {
            return new ModuleHealthResult(result.module(),
                    ModuleHealthStatus.DEGRADED,
                    result.checkedAt(), result.durationMs(), ModuleHealthReason.PASSIVE_TECHNICAL_ERRORS,
                    result.coverage(), result.dependencies());
        }
        return result;
    }

    public List<String> missingAdapters() {
        return CANONICAL_MODULES.stream().filter(module -> !adapters.containsKey(module)).toList();
    }

    public boolean hasCompleteCoverage() {
        return missingAdapters().isEmpty();
    }

    private ModuleHealthResult sanitize(String expectedModule, ModuleHealthResult result, Instant started) {
        if (result == null || !expectedModule.equals(canonicalize(result.module()))) {
            return ModuleHealthResult.unknown(expectedModule, Instant.now(clock), ModuleHealthReason.INVALID_RESULT,
                    List.of());
        }
        if (result.checkedAt().plusSeconds(30).isBefore(Instant.now(clock))) {
            return ModuleHealthResult.unknown(expectedModule, Instant.now(clock), ModuleHealthReason.CHECK_TIMEOUT,
                    List.of());
        }
        long duration = Math.max(result.durationMs(), Duration.between(started, Instant.now(clock)).toMillis());
        return new ModuleHealthResult(expectedModule, result.status(), result.checkedAt(), duration,
                result.reasonCode(), result.coverage(), result.dependencies().stream()
                        .filter(dependency -> dependency != null && SAFE_DEPENDENCY.matcher(dependency).matches())
                        .toList());
    }

    private static String canonicalize(String module) {
        if (module == null || module.isBlank()) {
            return "";
        }
        return module.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }

    private record CachedResult(ModuleHealthResult result, Instant checkedAt) { }

    /** Keeps a permit occupied until an interrupted adapter has actually returned. */
    private static final class ProbePermit {
        private final Semaphore semaphore;
        private boolean started;
        private boolean released;

        private ProbePermit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        synchronized boolean start() {
            if (released) {
                return false;
            }
            started = true;
            return true;
        }

        synchronized void cancelBeforeStart() {
            if (!started && !released) {
                released = true;
                semaphore.release();
            }
        }

        synchronized void release() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static final class ModuleNotFoundException extends RuntimeException {
        public ModuleNotFoundException(String module) {
            super("Módulo de observabilidade não encontrado: " + module);
        }
    }
}
