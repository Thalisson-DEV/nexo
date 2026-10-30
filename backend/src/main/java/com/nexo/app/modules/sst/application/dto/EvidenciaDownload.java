package com.nexo.app.modules.sst.application.dto;

import java.io.InputStream;

/**
 * Transporte interno do download de uma evidência: o stream do conteúdo (a ser
 * transmitido ao cliente) e os metadados necessários aos cabeçalhos HTTP. O
 * controlador é responsável por consumir/fechar o {@code conteudo}.
 */
public record EvidenciaDownload(InputStream conteudo, String contentType, long tamanhoBytes, String nomeArquivo) {
}
