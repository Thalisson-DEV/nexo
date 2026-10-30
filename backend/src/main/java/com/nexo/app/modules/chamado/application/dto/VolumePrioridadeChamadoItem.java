package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import java.math.BigDecimal;

public record VolumePrioridadeChamadoItem(
        PrioridadeChamado prioridade,
        long total,
        long anterior,
        BigDecimal variacaoPercentual,
        BigDecimal percentual
) {}
