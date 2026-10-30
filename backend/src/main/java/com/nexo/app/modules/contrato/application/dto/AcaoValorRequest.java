package com.nexo.app.modules.contrato.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AcaoValorRequest(
    @NotNull(message = "ID da ação é obrigatório")
    UUID idAcao,

    @NotNull(message = "ID do contrato é obrigatório")
    UUID idContrato,

    @NotNull(message = "O valor base é obrigatório")
    @PositiveOrZero(message = "O valor base não pode ser negativo")
    BigDecimal valorBase,

    @NotNull(message = "A data de vigência é obrigatória")
    LocalDate dataVigencia
) {}
