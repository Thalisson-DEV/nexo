package com.nexo.app.modules.chamado.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopicoDesempenhoItem(
        UUID id,
        String codigo,
        String nome,
        long totalChamados,
        long resolvidos,
        long pendentes,
        BigDecimal taxaSlaCumprido,
        BigDecimal tempoMedianoResolucaoHoras,
        long reabertos,
        BigDecimal participacaoPercentual
) {}
