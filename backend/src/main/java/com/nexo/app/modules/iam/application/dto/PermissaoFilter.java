package com.nexo.app.modules.iam.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

public record PermissaoFilter(
    String nome,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
