package com.nexo.app.modules.iam.application.dto;

import jakarta.validation.constraints.NotBlank;

public record AlterarSenhaRequest(
    @NotBlank(message = "A senha antiga é obrigatória")
    String senhaAntiga,

    @NotBlank(message = "A nova senha é obrigatória")
    String novaSenha
) {}
