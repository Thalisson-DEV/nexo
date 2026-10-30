package com.nexo.app.modules.automacao.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Decisão de um aprovador. */
public record RespostaAprovacaoRequest(
        @NotBlank String opcao,
        @Size(max = 1000) String comentario
) {
}
