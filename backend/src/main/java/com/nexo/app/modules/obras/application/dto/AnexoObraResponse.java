package com.nexo.app.modules.obras.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnexoObraResponse(
        UUID id,
        UUID idObra,
        UUID idTipoAnexo,
        String tipoAnexoNome,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        String observacoes,
        OffsetDateTime dataCriacao,
        String usuarioCriacao
) {
}
