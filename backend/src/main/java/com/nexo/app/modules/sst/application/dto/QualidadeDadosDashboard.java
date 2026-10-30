package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QualidadeDadosDashboard(
        OffsetDateTime ultimaCargaEm,
        long registrosSemPrazo,
        long registrosSemResponsavel,
        BigDecimal coberturaCadastroPercentual
) {}
