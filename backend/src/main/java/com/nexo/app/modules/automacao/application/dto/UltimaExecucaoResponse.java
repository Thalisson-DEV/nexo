package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.StatusExecucao;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Resumo da última execução, para a lista. */
public record UltimaExecucaoResponse(
        UUID id,
        StatusExecucao status,
        OffsetDateTime criadaEm,
        String erro
) {
}
