package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmarEntregaTecnicaRequestSecurityTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void exigeAsDuasAssinaturasNaoVazias() {
        ConfirmarEntregaTecnicaRequest request = request(null, "   ");

        assertThat(validator.validate(request))
                .extracting(violacao -> violacao.getPropertyPath().toString())
                .contains("assinaturaAlmox", "assinaturaEquipe");
    }

    @Test
    void limitaCadaAssinaturaA256KiB() {
        String acimaDoLimite = "x".repeat(262145);
        ConfirmarEntregaTecnicaRequest request = request(acimaDoLimite, acimaDoLimite);

        assertThat(validator.validate(request))
                .extracting(violacao -> violacao.getPropertyPath().toString())
                .contains("assinaturaAlmox", "assinaturaEquipe");
    }

    private static ConfirmarEntregaTecnicaRequest request(String assinaturaAlmox, String assinaturaEquipe) {
        return new ConfirmarEntregaTecnicaRequest(
                0L, UUID.randomUUID(), assinaturaAlmox, assinaturaEquipe,
                List.of(new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                        UUID.randomUUID(), BigDecimal.ONE, null)));
    }
}
