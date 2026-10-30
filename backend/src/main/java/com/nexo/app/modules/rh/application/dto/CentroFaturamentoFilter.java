package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Record para filtragem de Centros de Faturamento.
 */
public record CentroFaturamentoFilter(
    String nome,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {
}
