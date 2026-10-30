package com.nexo.app.modules.assistente.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenomearConversaRequest(@NotBlank @Size(max = 160) String titulo) {
}
