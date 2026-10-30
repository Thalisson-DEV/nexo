package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Item de ranking de estoque que mais solicitou material")
public record TopEstoqueItem(
    @Schema(description = "Nome do estoque")
    String nome,
    @Schema(description = "Total de solicitações realizadas")
    long totalSolicitacoes
) {}
