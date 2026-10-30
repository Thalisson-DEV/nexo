package com.nexo.app.modules.desconto.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MotivoDescontoRequest(
        @NotBlank(message = "A descrição é obrigatória.") String descricao,
        @NotNull(message = "O teto de parcelas é obrigatório.") @Min(value = 1, message = "O teto de parcelas deve ser pelo menos 1.") Integer tetoParcelas
) {}
