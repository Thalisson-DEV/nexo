package com.nexo.app.shared.infrastructure.observability;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Sanitized v1 result returned by a module health adapter. */
public record ModuleHealthResult(
        String module,
        ModuleHealthStatus status,
        Instant checkedAt,
        long durationMs,
        ModuleHealthReason reasonCode,
        ModuleHealthCoverage coverage,
        List<String> dependencies
) {

    public ModuleHealthResult {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(checkedAt, "checkedAt");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(coverage, "coverage");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs não pode ser negativo");
        }
    }

    public static ModuleHealthResult unknown(String module, Instant checkedAt,
                                             ModuleHealthReason reason,
                                             List<String> dependencies) {
        return new ModuleHealthResult(module, ModuleHealthStatus.UNKNOWN, checkedAt, 0,
                reason, ModuleHealthCoverage.FUNCTIONAL_READ, dependencies);
    }
}
