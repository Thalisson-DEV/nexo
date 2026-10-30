package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Filtro dinâmico para listagem de formulários de inspeção.
 */
public record FormularioInspecaoFilter(
        String titulo,
        Boolean ativo,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
        String buscaGlobal
) {
}
