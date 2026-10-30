package com.nexo.app.modules.contrato.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Record para filtragem dinâmica de contratos.
 *
 * @param numeroContrato Número do contrato (filtro parcial)
 * @param cliente Nome do cliente (filtro parcial)
 * @param ativo Status de ativação
 * @param buscaGlobal Busca rápida (LIKE, case/accent-insensitive)
 */
public record ContratoFilter(
    String numeroContrato,
    String cliente,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
