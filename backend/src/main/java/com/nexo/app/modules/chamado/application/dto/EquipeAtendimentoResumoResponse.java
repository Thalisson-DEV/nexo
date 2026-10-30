package com.nexo.app.modules.chamado.application.dto;

import java.util.UUID;

public record EquipeAtendimentoResumoResponse(
        UUID id,
        String codigo,
        String nome
) {}
