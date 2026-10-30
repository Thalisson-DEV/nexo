package com.nexo.app.modules.chamado.application.dto;

import java.math.BigDecimal;

public record IndicadorComparativoChamado(
        BigDecimal valor,
        BigDecimal anterior,
        BigDecimal variacaoPercentual,
        BigDecimal meta,
        String status,
        String definicao
) {}
