package com.nexo.app.modules.lookup.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LookupBootstrapEtagTest {

    private final LookupBootstrapEtag generator = new LookupBootstrapEtag(new ObjectMapper());

    @Test
    void etagEEstavelParaMesmoPayload() {
        LookupBootstrapDto body = body("Base Norte");

        assertThat(generator.generate(body)).isEqualTo(generator.generate(body))
                .matches("\"[0-9a-f]{64}\"");
    }

    @Test
    void etagMudaQuandoPayloadMuda() {
        assertThat(generator.generate(body("Base Norte")))
                .isNotEqualTo(generator.generate(body("Base Sul")));
    }

    @Test
    void etagDependeDaOrdemDoPayloadDoContrato() {
        LookupBootstrapDto first = body("Base Norte");
        LookupBootstrapDto reordered = new LookupBootstrapDto(
                List.of(new LookupOptionDto("base-2", "Base Sul"), new LookupOptionDto("base-1", "Base Norte")),
                first.movementTypes(), first.requestTypes(), first.statuses(), first.processes());

        assertThat(generator.generate(first)).isNotEqualTo(generator.generate(reordered));
    }

    private static LookupBootstrapDto body(String baseLabel) {
        return new LookupBootstrapDto(
                List.of(new LookupOptionDto("base-1", baseLabel)),
                List.of(new LookupOptionDto("ENTREGA", "Entrega")),
                List.of(new LookupOptionDto("221", "Adição comum")),
                List.of(new LookupOptionDto("PENDENTE", "Pendente")),
                List.of(new LookupOptionDto("processo-1", "Comercial")));
    }
}
