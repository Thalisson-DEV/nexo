package com.nexo.app.modules.notificacao.application.dto;

import java.util.UUID;

public record AnexoComunicadoResponse(
        UUID id,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes
) {
}
