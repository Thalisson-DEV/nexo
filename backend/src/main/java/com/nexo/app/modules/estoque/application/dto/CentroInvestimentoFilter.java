package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

public record CentroInvestimentoFilter(
    String codigo,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
