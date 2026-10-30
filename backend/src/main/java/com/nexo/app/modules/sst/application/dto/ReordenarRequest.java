package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Define a nova ordem de um conjunto de elementos (grupos, perguntas ou opções)
 * a partir da lista de IDs na ordem desejada. A lista deve conter exatamente os
 * mesmos itens existentes no container.
 */
public record ReordenarRequest(
        @NotEmpty(message = "A lista de IDs na nova ordem é obrigatória")
        List<UUID> idsNaOrdem
) {}
