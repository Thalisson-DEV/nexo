package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CriarExecucaoChecklistGovernancaRequest(
        @NotNull UUID idModelo,
        @NotNull UUID idCorpo,
        @NotNull UUID idBaseOperacional,
        UUID clientRequestId,
        @NotNull OffsetDateTime dataExecucao,
        @Size(max = 2000) String observacoes) {
}
