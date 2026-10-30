package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;

public record IndicadorComparativo(
        BigDecimal valor,
        BigDecimal anterior,
        BigDecimal variacaoPercentual,
        BigDecimal meta,
        String status,
        String definicao
) {}
