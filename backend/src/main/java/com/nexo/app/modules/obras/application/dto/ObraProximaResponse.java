package com.nexo.app.modules.obras.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Obra ativa no raio de outra, com distância em linha reta (km) para apoiar o agrupamento da programação.
 */
public record ObraProximaResponse(
        UUID id,
        String numero,
        String titulo,
        String statusNome,
        String municipioNome,
        BigDecimal distanciaKm,
        Double latitude,
        Double longitude
) {
}
