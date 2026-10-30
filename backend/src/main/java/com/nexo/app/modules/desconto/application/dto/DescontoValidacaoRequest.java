package com.nexo.app.modules.desconto.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DescontoValidacaoRequest(
        @NotNull(message = "O valor validado é obrigatório.")
        @DecimalMin(value = "0.00", message = "O valor validado não pode ser negativo.")
        BigDecimal valorValidado
) {}
