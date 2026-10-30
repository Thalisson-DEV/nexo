package com.nexo.app.modules.obras.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgenteResponsavelRequest(
        @NotBlank(message = "O nome do agente responsável é obrigatório")
        @Size(min = 2, max = 150, message = "O nome do agente deve ter entre 2 e 150 caracteres")
        String nome,

        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @Size(max = 30, message = "O telefone deve ter no máximo 30 caracteres")
        String telefone,

        @Size(max = 100, message = "A empresa/órgão deve ter no máximo 100 caracteres")
        String empresaOrgao
) {
}
