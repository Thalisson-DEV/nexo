package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MunicipioRequest(
    @NotBlank(message = "O nome do município é obrigatório")
    String nome,

    @NotNull(message = "O ID da base operacional é obrigatório")
    UUID idBaseOperacional
) {}
