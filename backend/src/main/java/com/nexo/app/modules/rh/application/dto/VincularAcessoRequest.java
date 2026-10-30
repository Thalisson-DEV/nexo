package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.iam.application.dto.UsuarioRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VincularAcessoRequest(
    @NotNull(message = "ID do colaborador é obrigatório")
    UUID colaboradorId,

    @Valid
    @NotNull(message = "Dados do usuário são obrigatórios")
    UsuarioRequest usuario
) {}
