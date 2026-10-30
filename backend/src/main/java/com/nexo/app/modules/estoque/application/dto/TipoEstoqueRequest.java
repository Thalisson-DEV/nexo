package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoEstoqueRequest(
    @NotBlank(message = "O nome do tipo de estoque é obrigatório")
    @Size(max = 20, message = "O nome deve ter no máximo 20 caracteres")
    String nome
) {}
