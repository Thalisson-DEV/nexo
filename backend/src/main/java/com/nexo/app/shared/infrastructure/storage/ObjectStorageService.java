package com.nexo.app.shared.infrastructure.storage;

import java.io.InputStream;

/**
 * Porta de armazenamento de objetos (arquivos binários) em provedor externo
 * compatível com S3 (ex.: Cloudflare R2). Abstrai os módulos de negócio do SDK
 * concreto — a implementação vive na infraestrutura compartilhada.
 */
public interface ObjectStorageService {

    /**
     * Envia (faz upload de) um objeto sob a {@code chave} informada. Sobrescreve se
     * a chave já existir.
     *
     * @param chave        identificador único do objeto no bucket (ex.: {@code sst/desvios/{id}/{uuid}.jpg})
     * @param conteudo     stream do conteúdo (será totalmente consumido)
     * @param tamanhoBytes tamanho exato em bytes (obrigatório — o provedor exige Content-Length conhecido)
     * @param contentType  MIME type do objeto
     */
    void enviar(String chave, InputStream conteudo, long tamanhoBytes, String contentType);

    /**
     * Recupera um objeto pela chave. O chamador deve fechar o stream retornado.
     */
    StorageObject baixar(String chave);

    /**
     * Remove o objeto da chave informada. Operação idempotente.
     */
    void remover(String chave);
}
