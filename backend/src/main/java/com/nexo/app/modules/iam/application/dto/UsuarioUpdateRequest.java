package com.nexo.app.modules.iam.application.dto;

import com.nexo.app.modules.iam.domain.TipoUsuario;
import jakarta.validation.constraints.Email;
import java.util.UUID;

public record UsuarioUpdateRequest(
    String login,

    String senha,

    @Email(message = "E-mail inválido")
    String email,

    String telefone,

    TipoUsuario tipo,

    UUID idPerfil
) {}
