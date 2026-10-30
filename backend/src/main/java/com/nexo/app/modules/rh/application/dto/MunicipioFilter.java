package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;

/**
 * Record para filtragem dinâmica de municípios.
 *
 * @param nome Nome do município (filtro parcial)
 * @param idBaseOperacional ID da base operacional vinculada
 * @param ativo Status de ativação
 * @param buscaGlobal Busca rápida (LIKE, case/accent-insensitive)
 */
public record MunicipioFilter(
    String nome,
    UUID idBaseOperacional,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
