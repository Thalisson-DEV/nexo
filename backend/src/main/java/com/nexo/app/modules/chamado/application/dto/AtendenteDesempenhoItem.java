package com.nexo.app.modules.chamado.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AtendenteDesempenhoItem(
        UUID id,
        String nome,
        String equipe,
        long totalChamados,
        long resolvidos,
        long pendentes,
        BigDecimal taxaSlaResolucao,
        BigDecimal tempoMedianoResolucaoHoras
) {}
