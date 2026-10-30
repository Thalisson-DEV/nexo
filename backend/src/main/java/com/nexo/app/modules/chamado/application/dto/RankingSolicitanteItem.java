package com.nexo.app.modules.chamado.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RankingSolicitanteItem(
        UUID id,
        String nome,
        long totalChamados,
        BigDecimal participacaoPercentual
) {}
