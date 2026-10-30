package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MotivoPendenciaRequest(
        @NotBlank @Size(min = 3, max = 100) String nome,
        @NotBlank @Size(min = 5, max = 255) String descricao
) {}
