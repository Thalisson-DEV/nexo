package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;

/**
 * Filtro dinâmico para listagem das atividades de SST.
 */
public record AtividadeSstFilter(

        @Parameter(description = "Atividades sob responsabilidade deste gestor")
        UUID idGestor,

        @Parameter(description = "Atividades de um processo de negócio")
        UUID idProcesso,

        Boolean ativo,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por nome e descrição")
        String buscaGlobal
) {
}
