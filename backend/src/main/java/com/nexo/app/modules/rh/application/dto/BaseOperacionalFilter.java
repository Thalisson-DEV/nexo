package com.nexo.app.modules.rh.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record para captura de parâmetros de filtro dinâmico de bases operacionais.
 */
public record BaseOperacionalFilter(
        String nome,
        Boolean ativo,
        LocalDate dataCriacao,
        UUID idCentroFaturamento,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
        String buscaGlobal
) {}
