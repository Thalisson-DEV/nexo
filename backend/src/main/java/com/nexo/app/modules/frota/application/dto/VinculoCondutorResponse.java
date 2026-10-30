package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.OrigemVinculoCondutor;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VinculoCondutorResponse(
        UUID id,
        UUID idVeiculo,
        String placa,
        UUID idColaborador,
        String condutor,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        OrigemVinculoCondutor origem,
        String observacao
) {
}
