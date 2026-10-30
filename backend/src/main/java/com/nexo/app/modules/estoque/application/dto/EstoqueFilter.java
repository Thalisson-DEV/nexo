package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;

public record EstoqueFilter(
    String nomeIdentificador,
    UUID idTipoEstoque,
    UUID idEquipe,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
