package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.ClasseTensao;

import java.util.UUID;

public record KitConstrutivoFilter(
        String buscaGlobal,
        UUID idContrato,
        String grupo,
        ClasseTensao classeTensao,
        Boolean ativo
) {
}
