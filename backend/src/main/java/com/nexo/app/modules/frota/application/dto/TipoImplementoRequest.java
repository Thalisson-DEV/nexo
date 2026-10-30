package com.nexo.app.modules.frota.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoImplementoRequest(
        @NotBlank(message = "Informe o nome do tipo de implemento.") @Size(max = 80) String nome,
        @Size(max = 255) String descricao
) {
}
