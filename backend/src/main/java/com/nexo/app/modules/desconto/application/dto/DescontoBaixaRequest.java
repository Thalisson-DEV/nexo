package com.nexo.app.modules.desconto.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DescontoBaixaRequest(
        @NotNull(message = "O valor baixado é obrigatório.")
        @DecimalMin(value = "0.00", message = "O valor baixado não pode ser negativo.")
        BigDecimal valorBaixado
) {}
