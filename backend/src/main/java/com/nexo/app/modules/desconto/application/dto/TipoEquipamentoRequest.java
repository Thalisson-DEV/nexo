package com.nexo.app.modules.desconto.application.dto;

import jakarta.validation.constraints.NotBlank;

public record TipoEquipamentoRequest(
        @NotBlank(message = "A descrição é obrigatória.") String descricao
) {}
