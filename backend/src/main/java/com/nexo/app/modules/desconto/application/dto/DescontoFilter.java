package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.StatusDesconto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DescontoFilter(
        UUID colaboradorId,
        UUID motivoId,
        StatusDesconto status,
        UUID usuarioSolicitanteId,
        OffsetDateTime dataCriacaoInicio,
        OffsetDateTime dataCriacaoFim,
        OffsetDateTime dataOcorrenciaInicio,
        OffsetDateTime dataOcorrenciaFim,
        String buscaGlobal
) {}
