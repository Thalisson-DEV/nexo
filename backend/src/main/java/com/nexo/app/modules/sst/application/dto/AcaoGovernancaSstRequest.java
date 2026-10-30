package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PrioridadeAcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoAcaoGovernancaSst;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record AcaoGovernancaSstRequest(
        @NotNull UUID idCorpo,
        @NotNull UUID idBaseOperacional,
        UUID idReuniao,
        UUID idExecucaoChecklist,
        UUID clientRequestId,
        @NotBlank @Size(max = 180) String titulo,
        @NotBlank @Size(max = 2000) String descricao,
        @NotNull TipoAcaoGovernancaSst tipo,
        @NotNull PrioridadeAcaoGovernancaSst prioridade,
        @NotNull UUID idResponsavel,
        @NotNull @FutureOrPresent LocalDate prazo,
        Long versaoEsperada) {
}
