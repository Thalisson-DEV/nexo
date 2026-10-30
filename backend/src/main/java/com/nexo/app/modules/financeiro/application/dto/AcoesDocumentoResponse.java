package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Acoes que o usuario atual pode executar sobre um documento financeiro. */
@Schema(description = "Acoes disponiveis para o documento no contexto da tela atual")
public record AcoesDocumentoResponse(
        boolean baixar,
        boolean editarMetadados,
        boolean remover,
        boolean conferir,
        boolean apontarPendencia,
        boolean arquivar,
        boolean reabrir,
        boolean desvincular
) {}
