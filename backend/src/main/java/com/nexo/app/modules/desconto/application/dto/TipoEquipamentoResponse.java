package com.nexo.app.modules.desconto.application.dto;

import java.util.UUID;

public record TipoEquipamentoResponse(
        UUID id,
        String descricao,
        boolean ativo
) {}
