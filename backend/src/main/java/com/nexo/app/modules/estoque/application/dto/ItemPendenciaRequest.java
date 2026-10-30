package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ItemPendenciaRequest(
        @NotNull UUID idItem,
        @NotNull UUID idMotivoPendencia,
        @NotNull @Size(min = 5, max = 2000) String justificativa
) {}
