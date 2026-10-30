package com.nexo.app.modules.sst.application.dto;

import java.util.UUID;

/**
 * Visão operacional de desvios ainda pendentes agrupados por uma dimensão gerencial.
 */
public record PendenciaAgrupadaResponse(
        UUID id,
        String nome,
        long totalPendentes,
        long totalAbertos,
        long totalEmTratamento,
        long totalVencidos,
        long totalCriticos
) {
}
