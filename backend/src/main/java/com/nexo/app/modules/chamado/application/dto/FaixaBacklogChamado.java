package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import java.math.BigDecimal;
import java.util.Map;

public record FaixaBacklogChamado(
        String faixa,
        String rotulo,
        long total,
        BigDecimal percentual,
        Map<PrioridadeChamado, Long> porPrioridade
) {}
