package com.nexo.app.modules.iam.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConfirmarImportacaoUsuarioRequest(
        @NotEmpty(message = "A lista de usuários não pode ser vazia")
        List<@NotNull(message = "O registro de usuário não pode ser nulo") UsuarioImportacaoDTO> usuarios
) {
}
