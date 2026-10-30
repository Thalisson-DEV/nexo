package com.nexo.app.modules.contrato.application.dto;

import com.nexo.app.modules.contrato.AcaoResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AcaoValorResponse(
    UUID id,
    AcaoResponse acao,
    ContratoResponse contrato,
    BigDecimal valorBase,
    LocalDate dataVigencia,
    boolean ativo,
    OffsetDateTime dataCriacao,
    OffsetDateTime dataDesativacao
) {}
