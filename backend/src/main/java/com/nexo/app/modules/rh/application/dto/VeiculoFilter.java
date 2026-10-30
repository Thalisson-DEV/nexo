package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;

/**
 * Record para captura de parâmetros de filtro dinâmico de veículos.
 */
public record VeiculoFilter(
    String placa,
    String modelo,
    UUID idBaseOperacional,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
