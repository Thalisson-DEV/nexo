package com.nexo.app.modules.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PerfilAcessoRequest(
    @NotBlank(message = "O nome do perfil é obrigatório")
    @Size(max = 50, message = "O nome do perfil não pode exceder 50 caracteres")
    String nome
) {}
