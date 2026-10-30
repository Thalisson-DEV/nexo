package com.nexo.app.modules.sst.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenciaGovernancaResponse(
        UUID id,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        String hashConteudo,
        OffsetDateTime dataCriacao) {
}
