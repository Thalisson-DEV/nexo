package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;

/**
 * Record para captura de parâmetros de filtro dinâmico de equipes.
 */
public record EquipeFilter(
    String codEquipe,
    UUID idTipoEquipe,
    UUID idProcesso,
    UUID idBaseOperacional,
    UUID idCoordenador,
    UUID idSupervisor,
    UUID idVeiculo,
    Boolean ativo,
    UUID idMembro,
    UUID idResponsavel,
    UUID idParceiro,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
