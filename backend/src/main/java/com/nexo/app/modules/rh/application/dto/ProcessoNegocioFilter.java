package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Record para captura de parâmetros de filtro dinâmico de processos de negócio.
 */
public record ProcessoNegocioFilter(
    String nome,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
