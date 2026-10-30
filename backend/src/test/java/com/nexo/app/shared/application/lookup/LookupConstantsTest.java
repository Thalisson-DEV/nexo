package com.nexo.app.shared.application.lookup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LookupConstantsTest {

    @Test
    void aplicaDefaultETetoAoLimiteSolicitado() {
        assertThat(LookupConstants.effectiveLimit(null)).isEqualTo(50);
        assertThat(LookupConstants.effectiveLimit(0)).isEqualTo(50);
        assertThat(LookupConstants.effectiveLimit(-10)).isEqualTo(50);
        assertThat(LookupConstants.effectiveLimit(75)).isEqualTo(75);
        assertThat(LookupConstants.effectiveLimit(101)).isEqualTo(100);
        assertThat(LookupConstants.DEFAULT_LIMIT).isEqualTo(50);
        assertThat(LookupConstants.MAX_LIMIT).isEqualTo(100);
        assertThat(LookupConstants.MIN_QUERY_LENGTH).isEqualTo(2);
    }
}
