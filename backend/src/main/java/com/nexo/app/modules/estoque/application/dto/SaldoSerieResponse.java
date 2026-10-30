package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaldoSerieResponse(
    UUID id,
    EstoqueResponse estoque,
    MaterialResponse material,
    String numSerie,
    CondicaoMaterial condicao,
    boolean ativo,
    /** Desde quando a série está neste estoque (entrada, transferência ou entrega). */
    OffsetDateTime noSaldoDesde,
    /** Solicitação para a qual a série está reservada; nulo quando está livre. */
    UUID reservadaParaSolicitacao
) {}
