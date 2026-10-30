package com.nexo.app.shared.infrastructure.lookup;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupRateLimitPropertiesTest {

    @Test
    void possuiDefaultsSegurosEConverteJanelaParaSegundos() {
        LookupRateLimitProperties properties = new LookupRateLimitProperties();

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.requestsPerMinute()).isEqualTo(300);
        assertThat(properties.windowSeconds()).isEqualTo(60);
    }

    @Test
    void rejeitaLimiteOuJanelaNaoPositivos() {
        assertThatThrownBy(() -> new LookupRateLimitProperties(true, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupRateLimitProperties(true, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
