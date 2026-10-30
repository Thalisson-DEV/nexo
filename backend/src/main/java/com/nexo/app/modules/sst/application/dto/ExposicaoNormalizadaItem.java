package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExposicaoNormalizadaItem(
        UUID id,
        String nome,
        long inspecoes,
        long desvios,
        BigDecimal desviosPor100Inspecoes,
        long criticosAbertos,
        long vencidos,
        BigDecimal variacaoTaxaPercentual
) {}
