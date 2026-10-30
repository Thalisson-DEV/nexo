package com.nexo.app.shared.infrastructure.storage;

import java.io.InputStream;

/**
 * Objeto recuperado do armazenamento externo: o conteúdo binário (stream) e seus
 * metadados essenciais. O consumidor é responsável por fechar o {@code conteudo}.
 */
public record StorageObject(InputStream conteudo, String contentType, long tamanhoBytes) {
}
