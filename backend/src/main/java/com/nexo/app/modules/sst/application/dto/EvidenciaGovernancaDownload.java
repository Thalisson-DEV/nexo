package com.nexo.app.modules.sst.application.dto;

import java.io.InputStream;

public record EvidenciaGovernancaDownload(
        InputStream conteudo,
        String contentType,
        long tamanhoBytes,
        String nomeArquivo) {
}
