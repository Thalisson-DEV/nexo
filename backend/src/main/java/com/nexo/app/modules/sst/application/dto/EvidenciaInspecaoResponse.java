package com.nexo.app.modules.sst.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadados de uma evidência fotográfica da inspeção. O conteúdo binário é obtido
 * separadamente no endpoint de download.
 *
 * <p>No envio pré-lançamento, o {@code id} devolvido é o que o cliente manda de volta
 * em {@code evidencias} ao registrar a inspeção.</p>
 */
public record EvidenciaInspecaoResponse(
        UUID id,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        OffsetDateTime dataCriacao
) {}
