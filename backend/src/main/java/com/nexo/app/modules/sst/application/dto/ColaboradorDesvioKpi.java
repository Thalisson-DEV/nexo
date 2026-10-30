package com.nexo.app.modules.sst.application.dto;

import java.util.UUID;

/**
 * Indicadores consolidados de desvios por colaborador.
 */
public record ColaboradorDesvioKpi(
        UUID idColaborador,
        String nome,
        long totalDesvios,
        long abertos,
        long emTratamento,
        long resolvidos,
        long criticos
) {}
