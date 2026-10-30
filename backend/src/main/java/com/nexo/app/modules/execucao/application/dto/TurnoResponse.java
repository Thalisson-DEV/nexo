package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.StatusTurno;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record TurnoResponse(
    UUID id,
    UUID idEquipe,
    UUID idUsuarioAbertura,
    OffsetDateTime dataInicio,
    OffsetDateTime dataFim,
    StatusTurno status,
    UUID idVeiculo,
    Set<UUID> idsColaboradores,
    /* A equipe esqueceu o turno aberto e o servidor o encerrou no horário de corte. */
    boolean encerradoAutomaticamente,
    String motivoEncerramento
) {}
