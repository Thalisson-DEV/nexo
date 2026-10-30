package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.StatusEtapa;
import java.time.OffsetDateTime;
import java.util.Map;

/** Uma etapa de uma execução, com entradas e saídas. */
public record EtapaResponse(
        String noId,
        String iteracao,
        String tipo,
        StatusEtapa status,
        int tentativa,
        Map<String, Object> entradas,
        Map<String, Object> saidas,
        String erro,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        OffsetDateTime proximaEm
) {
}
