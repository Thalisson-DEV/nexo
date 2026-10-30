package com.nexo.app.shared.infrastructure.observability;

/** Stable, non-sensitive reason codes for the v1 monitoring contract. */
public enum ModuleHealthReason {
    CHECK_SUCCEEDED,
    CHECK_FAILED,
    ADAPTER_MISSING,
    CHECK_TIMEOUT,
    PROBE_CAPACITY,
    PASSIVE_TECHNICAL_ERRORS,
    INVALID_RESULT,
    DISABLED
}
