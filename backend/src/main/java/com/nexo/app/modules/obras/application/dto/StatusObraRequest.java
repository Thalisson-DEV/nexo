package com.nexo.app.modules.obras.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusObraRequest(
        @NotBlank(message = "O nome do status é obrigatório")
        @Size(min = 2, max = 60, message = "O nome do status deve ter entre 2 e 60 caracteres")
        String nome,

        Integer ordem,

        Boolean concluida
) {
}
