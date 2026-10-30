package com.nexo.app.modules.chamado.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EquipeDesempenhoItem(
        UUID id,
        String codigo,
        String nome,
        long totalChamados,
        long resolvidos,
        long pendentes,
        BigDecimal taxaSlaPrimeiraResposta,
        BigDecimal taxaSlaResolucao,
        BigDecimal tempoMedianoResolucaoHoras,
        BigDecimal participacaoPercentual
) {}
