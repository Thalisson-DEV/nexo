package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.OrigemLeituraOdometro;
import com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LeituraOdometroResponse(
        UUID id,
        UUID idVeiculo,
        String placa,
        Long km,
        OffsetDateTime lidoEm,
        OrigemLeituraOdometro origem,
        UUID idOrigem,
        SituacaoLeituraOdometro situacao,
        String motivoSituacao,
        Long kmAnterior,
        String registradoPor,
        OffsetDateTime dataAnulacao,
        String justificativaAnulacao
) {
}
