package com.nexo.app.modules.sst.domain;

/**
 * Natureza do anexo de um desvio, derivada do MIME type: imagens são {@link #FOTO},
 * os demais formatos aceitos (PDF, Word) são {@link #DOCUMENTO}.
 */
public enum TipoAnexo {
    FOTO,
    DOCUMENTO;

    public static TipoAnexo deContentType(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("image/") ? FOTO : DOCUMENTO;
    }
}
