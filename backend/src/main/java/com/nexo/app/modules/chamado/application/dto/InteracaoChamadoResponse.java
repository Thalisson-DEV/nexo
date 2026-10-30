package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.modules.chamado.domain.TipoInteracaoChamado;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InteracaoChamadoResponse(
        UUID id,
        TipoInteracaoChamado tipo,
        String mensagem,
        boolean interno,
        UUID usuarioAutorId,
        StatusChamado statusAnterior,
        StatusChamado statusNovo,
        UUID equipeAnteriorId,
        UUID equipeNovaId,
        UUID responsavelAnteriorId,
        UUID responsavelNovoId,
        OffsetDateTime dataEvento
) {}
