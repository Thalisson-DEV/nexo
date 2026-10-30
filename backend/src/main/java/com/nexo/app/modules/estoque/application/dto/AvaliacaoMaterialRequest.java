package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AvaliacaoMaterialRequest(
    @NotBlank(message = "O nome da avaliação é obrigatório")
    @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres")
    String nome
) {}
