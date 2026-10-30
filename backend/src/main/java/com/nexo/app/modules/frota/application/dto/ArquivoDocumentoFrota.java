package com.nexo.app.modules.frota.application.dto;

import java.io.InputStream;

/** Anexo de um documento para download. Quem recebe fecha o stream. */
public record ArquivoDocumentoFrota(String nome, String contentType, long tamanhoBytes, InputStream conteudo) {
}
