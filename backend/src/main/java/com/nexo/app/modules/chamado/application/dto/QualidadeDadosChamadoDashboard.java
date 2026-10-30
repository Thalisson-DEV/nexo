package com.nexo.app.modules.chamado.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QualidadeDadosChamadoDashboard(
        OffsetDateTime ultimaCargaEm,
        long chamadosSemResponsavel,
        long chamadosComSlaPausado,
        long chamadosEstagnadosSemInteracao,
        BigDecimal coberturaResponsavelPercentual
) {}
