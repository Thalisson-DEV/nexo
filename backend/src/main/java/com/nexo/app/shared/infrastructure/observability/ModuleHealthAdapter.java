package com.nexo.app.shared.infrastructure.observability;

/**
 * SPI exposed to modules that want to provide a small, read-only health probe.
 *
 * <p>The implementation belongs to the module that owns the data. Consumers of
 * this SPI must not reach into another module's repositories or use cases.</p>
 */
public interface ModuleHealthAdapter {

    /** Canonical module key used by the monitoring catalog. */
    String module();

    /**
     * Performs the module-local, read-only probe.
     *
     * <p>Implementations must honor the deadline from the context and must not
     * mutate business data or publish notifications.</p>
     */
    ModuleHealthResult check(ModuleHealthCheckContext context);
}
