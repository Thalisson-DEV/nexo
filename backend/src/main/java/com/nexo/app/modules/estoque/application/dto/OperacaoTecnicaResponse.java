package com.nexo.app.modules.estoque.application.dto;

import java.util.UUID;

/** Identificador da operação técnica imutável criada por um comando de movimentação. */
public record OperacaoTecnicaResponse(UUID idOperacao) {
}
