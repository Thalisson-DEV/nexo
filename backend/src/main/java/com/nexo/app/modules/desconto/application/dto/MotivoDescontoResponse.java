package com.nexo.app.modules.desconto.application.dto;

import java.util.UUID;

public record MotivoDescontoResponse(
        UUID id,
        String descricao,
        Integer tetoParcelas,
        boolean ativo
) {}
