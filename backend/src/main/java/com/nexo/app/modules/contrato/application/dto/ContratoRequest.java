package com.nexo.app.modules.contrato.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoRequest(
    @NotBlank(message = "Número do contrato é obrigatório")
    String numeroContrato,

    @NotBlank(message = "Cliente é obrigatório")
    String cliente,

    @NotNull(message = "Data de vigência é obrigatória")
    LocalDate dataVigencia,

    @NotNull(message = "Data de encerramento é obrigatória")
    LocalDate dataEncerramento,

    @NotNull(message = "O coeficiente de faturamento é obrigatório")
    @DecimalMin(value = "0.0001", message = "O coeficiente de faturamento deve ser maior que zero")
    @DecimalMax(value = "1000", message = "O coeficiente de faturamento deve ser menor ou igual a 1000")
    BigDecimal coeficienteFaturamento
) {}
