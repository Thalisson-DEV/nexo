package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PrioridadeAcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusAcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoAcaoGovernancaSst;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AcaoGovernancaSstFilter(
        String buscaGlobal,
        List<UUID> idsCorpos,
        List<UUID> idsBases,
        UUID idResponsavel,
        List<StatusAcaoGovernancaSst> status,
        List<PrioridadeAcaoGovernancaSst> prioridades,
        List<TipoAcaoGovernancaSst> tipos,
        LocalDate prazoInicio,
        LocalDate prazoFim,
        Boolean vencidas) {
}
