package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DefinirSeriesReservadasRequest(
        @Schema(description = "Números de série que ficam reservados para o item; a lista substitui a anterior. Vazia libera tudo.")
        @NotNull(message = "Informe a lista de séries")
        List<String> series
) {}
