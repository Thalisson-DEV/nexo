package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SaldoResponse(
    UUID id,
    EstoqueResponse estoque,
    MaterialResponse material,
    CondicaoMaterial condicao,
    BigDecimal quantidade,
    boolean negativo,
    boolean ativo,
    /** Última vez que a quantidade mudou: base do "parado há N dias" no app de campo. */
    OffsetDateTime ultimaMovimentacao
) {}
