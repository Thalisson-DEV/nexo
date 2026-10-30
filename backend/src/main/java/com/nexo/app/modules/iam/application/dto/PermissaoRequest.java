package com.nexo.app.modules.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissaoRequest(
    @NotBlank(message = "O nome da permissão é obrigatório")
    @Size(min = 3, max = 100, message = "O nome da permissão deve ter entre 3 e 100 caracteres")
    String nome
) {}
