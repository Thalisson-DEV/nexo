package com.nexo.app.modules.assistente.application.navigation;

import com.nexo.app.modules.assistente.application.dto.AcaoInterface;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationActionCollectorTest {

    @Test
    void deduplicatesAndLimitsActionsToThree() {
        var collector = new NavigationActionCollector();
        collector.record(action("A"));
        collector.record(action("A"));
        collector.record(action("B"));
        collector.record(action("C"));
        collector.record(action("D"));

        assertThat(collector.snapshot()).extracting(AcaoInterface::chaveDestino)
                .containsExactly("A", "B", "C");
    }

    private AcaoInterface action(String key) {
        return new AcaoInterface("NAVIGATE", key, key, "authority", Map.of());
    }
}
