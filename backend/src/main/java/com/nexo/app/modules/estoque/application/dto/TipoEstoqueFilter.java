package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

public record TipoEstoqueFilter(
    String nome,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
