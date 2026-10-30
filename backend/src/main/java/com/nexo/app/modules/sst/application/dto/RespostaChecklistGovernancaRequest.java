package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RespostaChecklistGovernancaRequest(
        @NotNull UUID idPergunta,
        @Size(max = 2000) String valorTexto,
        BigDecimal valorNumero,
        LocalDate valorData,
        List<UUID> idsOpcoes) {
}
