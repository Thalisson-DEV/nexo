package com.nexo.app.modules.contrato.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcaoRequest(
    @NotBlank(message = "Código da ação é obrigatório")
    @Size(max = 20, message = "O código deve ter no máximo 20 caracteres")
    String codigo,

    @NotBlank(message = "Nome da ação é obrigatório")
    @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
    String nome
) {}
