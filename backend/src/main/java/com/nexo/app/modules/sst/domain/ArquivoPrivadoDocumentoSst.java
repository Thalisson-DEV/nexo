package com.nexo.app.modules.sst.domain;

public record ArquivoPrivadoDocumentoSst(
        String nomeArquivo,
        String objectKey,
        String contentType,
        long tamanhoBytes,
        String sha256
) {
}
