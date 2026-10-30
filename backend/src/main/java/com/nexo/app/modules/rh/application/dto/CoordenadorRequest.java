package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CoordenadorRequest(
    @NotNull(message = "O ID do colaborador é obrigatório")
    UUID idColaborador,

    @NotNull(message = "A data de início é obrigatória")
    LocalDate dataInicio,

    LocalDate dataFim
) {}
