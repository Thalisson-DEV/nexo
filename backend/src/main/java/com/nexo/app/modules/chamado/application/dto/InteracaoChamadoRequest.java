package com.nexo.app.modules.chamado.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InteracaoChamadoRequest(
        @NotBlank(message = "Informe a mensagem.")
        @Size(max = 5000, message = "A mensagem deve ter no máximo 5.000 caracteres.")
        String mensagem,
        boolean interno
) {}
