package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Acopla o implemento ao veículo a partir de {@code inicio}; se estava em outro, encerra lá no mesmo instante. */
public record AcoplarImplementoRequest(
        @NotNull(message = "Informe o veículo.") UUID idVeiculo,
        @NotNull(message = "Informe a data e hora do acoplamento.") OffsetDateTime inicio,
        @Size(max = 500) String observacao
) {
}
