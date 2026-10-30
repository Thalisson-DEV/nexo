package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Item de ranking de motivo mais frequente de rejeição")
public record TopMotivoItem(
    @Schema(description = "Nome do motivo")
    String nome,
    @Schema(description = "Total de vezes que foi utilizado")
    long totalOcorrencias
) {}
