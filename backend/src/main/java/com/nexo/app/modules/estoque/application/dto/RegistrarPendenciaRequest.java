package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RegistrarPendenciaRequest(
        UUID idMotivoPendencia,
        @Size(max = 2000) String justificativa,
        UUID idItem,
        List<UUID> idsItens,
        @Valid List<ItemPendenciaRequest> itensPendencias
) {
    public RegistrarPendenciaRequest(UUID idMotivoPendencia, String justificativa, UUID idItem) {
        this(idMotivoPendencia, justificativa, idItem, null, null);
    }

    public RegistrarPendenciaRequest(UUID idMotivoPendencia, String justificativa, UUID idItem, List<UUID> idsItens) {
        this(idMotivoPendencia, justificativa, idItem, idsItens, null);
    }
}
