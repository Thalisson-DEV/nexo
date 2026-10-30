package com.nexo.app.shared.infrastructure.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Common execution policy for module-local, read-only probes.
 *
 * <p>The callback is deliberately opaque to this shared module: a module owns
 * its repository and supplies only the read operation. No entity, identifier,
 * exception message, or business payload crosses the observability boundary.</p>
 */
public final class ReadOnlyModuleHealthProbe {

    private ReadOnlyModuleHealthProbe() {
    }

    public static ModuleHealthResult execute(
            String module,
            ModuleHealthCheckContext context,
            ModuleHealthCoverage coverage,
            List<String> dependencies,
            Runnable readOperation) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(readOperation, "readOperation");

        Instant started = Instant.now(context.clock());
        if (context.expired()) {
            return ModuleHealthResult.unknown(module, started, ModuleHealthReason.CHECK_TIMEOUT, dependencies);
        }

        try {
            readOperation.run();
            Instant checkedAt = Instant.now(context.clock());
            long duration = Math.max(0, Duration.between(started, checkedAt).toMillis());
            if (context.expired()) {
                return ModuleHealthResult.unknown(module, checkedAt, ModuleHealthReason.CHECK_TIMEOUT, dependencies);
            }
            return new ModuleHealthResult(module, ModuleHealthStatus.UP, checkedAt, duration,
                    ModuleHealthReason.CHECK_SUCCEEDED, coverage, dependencies);
        } catch (RuntimeException exception) {
            Instant checkedAt = Instant.now(context.clock());
            long duration = Math.max(0, Duration.between(started, checkedAt).toMillis());
            return new ModuleHealthResult(module, ModuleHealthStatus.DOWN, checkedAt, duration,
                    ModuleHealthReason.CHECK_FAILED, coverage, dependencies);
        }
    }
}
