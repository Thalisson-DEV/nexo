package com.nexo.app.modules.chamado.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReabrirChamadoRequest(
        @NotBlank(message = "Informe o motivo da reabertura.")
        @Size(max = 5000, message = "O motivo deve ter no máximo 5.000 caracteres.")
        String motivo
) {}
