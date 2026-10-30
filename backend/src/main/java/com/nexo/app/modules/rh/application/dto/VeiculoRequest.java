package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VeiculoRequest(
    @NotBlank(message = "A placa é obrigatória")
    String placa,

    @NotBlank(message = "O modelo é obrigatório")
    String modelo,

    @NotBlank(message = "O fabricante é obrigatório")
    String fabricante,

    @NotNull(message = "A base operacional é obrigatória")
    UUID baseOperacionalId
) {}
