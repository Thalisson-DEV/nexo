package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PrioridadeAcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusAcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusPrazoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoAcaoGovernancaSst;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AcaoGovernancaSstResponse(
        UUID id,
        UUID idCorpo,
        UUID idBaseOperacional,
        UUID idReuniao,
        UUID idExecucaoChecklist,
        String titulo,
        String descricao,
        TipoAcaoGovernancaSst tipo,
        PrioridadeAcaoGovernancaSst prioridade,
        UUID idResponsavel,
        String nomeResponsavel,
        LocalDate prazo,
        StatusAcaoGovernancaSst status,
        StatusPrazoGovernancaSst statusPrazo,
        String resultado,
        String motivoCancelamento,
        OffsetDateTime dataInicio,
        OffsetDateTime dataConclusao,
        OffsetDateTime dataCancelamento,
        Long versao,
        OffsetDateTime dataCriacao) {
}
