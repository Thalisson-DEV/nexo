package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ContextoAnexoEvento;
import com.nexo.app.modules.sst.domain.TipoAnexo;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadados de uma evidência do evento. O conteúdo binário é baixado no endpoint
 * próprio; aqui trafega apenas o que a tela precisa para listar.
 */
public record AnexoEventoResponse(
        UUID id,
        ContextoAnexoEvento contexto,
        TipoAnexo tipo,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        OffsetDateTime dataCriacao
) {
}
