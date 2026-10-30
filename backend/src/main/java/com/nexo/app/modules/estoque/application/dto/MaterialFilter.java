package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;

public record MaterialFilter(
    String codigo,
    String descricao,
    UUID idProcesso,
    UUID idAvaliacaoTecnica,
    UUID idCentroInvestimento,
    Boolean detalhaSerie,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
