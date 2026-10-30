package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCriticoRecorrente(
        UUID idPergunta,
        String item,
        long respostas,
        long reprovacoes,
        BigDecimal taxaReprovacao,
        GravidadeDesvio maiorGravidade,
        BigDecimal variacaoTaxaPercentual,
        BigDecimal percentualAcumulado
) {}
