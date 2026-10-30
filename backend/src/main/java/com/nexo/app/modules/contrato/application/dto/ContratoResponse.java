package com.nexo.app.modules.contrato.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContratoResponse(
    UUID id,
    String numeroContrato,
    String cliente,
    LocalDate dataVigencia,
    LocalDate dataEncerramento,
    BigDecimal coeficienteFaturamento,
    boolean ativo,
    OffsetDateTime dataCriacao,
    OffsetDateTime dataDesativacao
) {}
