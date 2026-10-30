package com.nexo.app.modules.financeiro.application.dto;

import java.io.InputStream;

/**
 * Conteúdo de um documento pronto para ser transmitido pelo controller.
 *
 * @param conteudo     fluxo de bytes vindo do object storage
 * @param contentType  tipo do conteúdo, para o cabeçalho da resposta
 * @param tamanhoBytes tamanho declarado, para o cabeçalho de comprimento
 * @param nomeArquivo  nome original, para o cabeçalho de disposição
 */
public record DocumentoDownload(
        InputStream conteudo,
        String contentType,
        Long tamanhoBytes,
        String nomeArquivo
) {}
