package com.nexo.app.modules.iam.application.dto;

import com.nexo.app.modules.iam.domain.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UsuarioRequest(
    @NotBlank(message = "Login é obrigatório")
    String login,

    @Email(message = "E-mail inválido")
    String email,

    String telefone,

    @NotBlank(message = "Senha é obrigatória")
    String senha,

    @NotNull(message = "ID do Perfil é obrigatório")
    UUID idPerfil,

    UUID idColaborador,

    @NotNull(message = "Tipo de usuário é obrigatório")
    TipoUsuario tipo
) {}
