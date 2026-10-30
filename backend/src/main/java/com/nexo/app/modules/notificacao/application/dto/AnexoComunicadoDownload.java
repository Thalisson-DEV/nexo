package com.nexo.app.modules.notificacao.application.dto;

import java.io.InputStream;

/**
 * Conteúdo do anexo pronto para a resposta HTTP. Carrega o nome do arquivo, que o
 * {@code StorageObject} não guarda e o navegador precisa para nomear o download.
 */
public record AnexoComunicadoDownload(
        InputStream conteudo,
        String contentType,
        long tamanhoBytes,
        String nomeArquivo
) {
}
