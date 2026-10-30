package com.nexo.app.shared.infrastructure.observability;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModuleHealthCoverage {
    FUNCTIONAL_READ("functional-read"),
    CONFIGURATION_ONLY("configuration-only");

    private final String value;

    ModuleHealthCoverage(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
