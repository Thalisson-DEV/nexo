package com.nexo.app.modules.obras.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoAnexoObraRequest(
        @NotBlank(message = "O nome do tipo de anexo é obrigatório")
        @Size(min = 2, max = 60, message = "O nome do tipo de anexo deve ter entre 2 e 60 caracteres")
        String nome,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String descricao
) {
}
