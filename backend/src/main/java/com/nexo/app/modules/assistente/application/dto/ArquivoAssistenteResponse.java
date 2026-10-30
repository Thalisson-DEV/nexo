package com.nexo.app.modules.assistente.application.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ArquivoAssistenteResponse(
        UUID id,
        String codigo,
        UUID conversaId,
        String nome,
        String tipoConteudo,
        String categoria,
        long tamanhoBytes,
        String origem,
        String status,
        String erro,
        Map<String, Object> resumo,
        OffsetDateTime criadoEm) {
}
