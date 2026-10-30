package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Item de ranking de material mais solicitado")
public record TopMaterialItem(
    @Schema(description = "Código do material")
    String codigo,
    @Schema(description = "Nome do material")
    String nome,
    @Schema(description = "Quantidade total solicitada")
    long totalSolicitado
) {}
