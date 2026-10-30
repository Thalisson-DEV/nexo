package com.nexo.app.modules.chamado.application.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record EquipeAtendimentoResponse(
        UUID id,
        String codigo,
        String nome,
        String descricao,
        Set<UUID> membros,
        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao
) {}
