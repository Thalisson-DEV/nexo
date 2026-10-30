package com.nexo.app.modules.obras.application.dto;

import java.math.BigDecimal;

/**
 * Totais do recorte filtrado da listagem de obras.
 */
public record IndicadoresCarteiraResponse(
        long quantidade,
        BigDecimal valorProjeto,
        BigDecimal valorMaterialPrevisto,
        BigDecimal valorServicoPrevisto,
        long municipios
) {
}
