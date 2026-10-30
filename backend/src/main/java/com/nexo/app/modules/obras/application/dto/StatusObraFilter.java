package com.nexo.app.modules.obras.application.dto;

public record StatusObraFilter(
        String buscaGlobal,
        String nome,
        Boolean concluida,
        Boolean ativo
) {
}
