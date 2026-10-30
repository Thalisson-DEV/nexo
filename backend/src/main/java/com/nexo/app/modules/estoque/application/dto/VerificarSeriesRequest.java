package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Séries a conferir antes de uma entrada: as que já estão ativas em algum estoque voltam na resposta. */
public record VerificarSeriesRequest(
        @NotNull(message = "Informe as séries")
        @Size(max = 2000, message = "Confira no máximo 2000 séries por vez")
        List<String> series
) {}
