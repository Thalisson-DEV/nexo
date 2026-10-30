package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Nova ordem de exibição, informada como a permutação completa dos IDs existentes.
 *
 * <p>Exigir a lista inteira, em vez de "mova o item X para a posição N", torna a
 * operação idempotente e imune a corridas entre dois cadastradores.</p>
 */
public record ReordenarRequest(

        @Schema(description = "IDs na ordem desejada — exatamente os elementos existentes")
        @NotEmpty(message = "Informe a nova ordem.")
        List<UUID> ids
) {}
