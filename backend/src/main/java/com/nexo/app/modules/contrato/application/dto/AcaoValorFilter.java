package com.nexo.app.modules.contrato.application.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record para filtragem dinâmica de valores de ações.
 *
 * @param idAcao ID da ação
 * @param idContrato ID do contrato
 * @param dataVigencia Data de vigência do valor
 * @param ativo Status de ativação
 * @param buscaGlobal Busca rápida (LIKE, case/accent-insensitive) por ação/contrato
 */
public record AcaoValorFilter(
    UUID idAcao,
    UUID idContrato,
    LocalDate dataVigencia,
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {}
