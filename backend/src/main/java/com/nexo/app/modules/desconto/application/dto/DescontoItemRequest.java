package com.nexo.app.modules.desconto.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DescontoItemRequest(
        @NotBlank(message = "A descrição do material é obrigatória.") String descricaoMaterial,
        @NotNull(message = "O ID do tipo de equipamento é obrigatório.") UUID tipoEquipamentoId,
        @NotNull(message = "O valor é obrigatório.") @Min(value = 0, message = "O valor não pode ser negativo.") BigDecimal valor
) {}
