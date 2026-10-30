package com.nexo.app.modules.desconto.application.dto;

import java.io.InputStream;

public record DescontoAnexoDownload(
        InputStream conteudo,
        String contentType,
        Long tamanhoBytes,
        String nomeArquivo
) {}
