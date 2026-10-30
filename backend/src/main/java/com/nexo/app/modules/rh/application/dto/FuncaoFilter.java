package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Record para captura de parâmetros de filtro dinâmico de funções de colaboradores.
 */
public record FuncaoFilter(
    String nome,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
