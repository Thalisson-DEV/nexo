package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CentroInvestimentoRequest(
    @NotBlank(message = "O código do centro de investimento é obrigatório")
    @Size(max = 10, message = "O código deve ter no máximo 10 caracteres")
    String codigo
) {}
