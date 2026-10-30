package com.nexo.app.modules.automacao.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Pedido em português para o Copiloto montar (ou ajustar) uma automação. */
public record RascunhoRequest(
        @NotBlank @Size(max = 4000) String pedido,
        UUID automacaoId
) {
}
