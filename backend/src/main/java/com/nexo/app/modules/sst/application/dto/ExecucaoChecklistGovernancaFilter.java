package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusExecucaoChecklistGovernancaSst;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExecucaoChecklistGovernancaFilter(
        List<UUID> idsCorpos,
        List<UUID> idsBases,
        UUID idModelo,
        UUID idExecutor,
        List<StatusExecucaoChecklistGovernancaSst> status,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim) {
}
