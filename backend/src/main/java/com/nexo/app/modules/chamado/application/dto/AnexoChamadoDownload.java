package com.nexo.app.modules.chamado.application.dto;

import java.io.InputStream;

public record AnexoChamadoDownload(
        InputStream conteudo,
        String contentType,
        long tamanhoBytes,
        String nomeArquivo
) {}
