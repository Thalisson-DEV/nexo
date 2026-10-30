package com.nexo.app.modules.obras.application.dto;

import java.io.InputStream;

public record DownloadAnexoObraDTO(
        InputStream stream,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes
) {
}
