package com.nexo.app.modules.chamado.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnexoChamadoResponse(
        UUID id,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        OffsetDateTime dataCriacao
) {}
