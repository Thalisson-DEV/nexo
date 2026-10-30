package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Período consultado e período anterior equivalente, com bordas inclusivas")
public record PeriodoDashboard(
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String timezone,
        OffsetDateTime comparacaoInicio,
        OffsetDateTime comparacaoFim
) {}
