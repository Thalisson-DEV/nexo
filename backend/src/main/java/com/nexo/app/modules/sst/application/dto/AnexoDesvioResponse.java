package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.modules.sst.domain.TipoAnexo;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadados de uma evidência (anexo) de um desvio. O conteúdo binário é obtido
 * separadamente no endpoint de download do anexo.
 */
public record AnexoDesvioResponse(
        UUID id,
        ContextoAnexo contexto,
        TipoAnexo tipo,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        OffsetDateTime dataCriacao
) {}
