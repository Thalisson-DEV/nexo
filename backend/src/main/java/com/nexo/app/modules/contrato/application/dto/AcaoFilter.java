package com.nexo.app.modules.contrato.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Record para filtragem dinâmica de ações.
 *
 * @param codigo Código da ação (filtro parcial)
 * @param nome Nome da ação (filtro parcial)
 * @param ativo Status de ativação
 * @param buscaGlobal Busca rápida (LIKE, case/accent-insensitive)
 */
public record AcaoFilter(
    String codigo,
    String nome,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
