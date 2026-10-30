package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.modules.execucao.domain.StatusTurno;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Linha da consulta de turnos: o turno, a equipe e o que ela produziu nele.
 *
 * <p>A meta diária é a da equipe no dia da abertura. Se a equipe teve mais de um turno
 * no dia, cada linha compara só a produção daquele turno com a meta do dia.</p>
 */
public record TurnoConsultaResponse(
        UUID id,
        UUID idEquipe,
        String codigoEquipe,
        String baseOperacional,
        String processo,
        StatusTurno status,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        /* Até o fechamento ou, se ainda aberto, até agora. */
        long duracaoMinutos,
        /* Aberto e iniciado antes de hoje: provável turno esquecido. */
        boolean abertoDesdeOutroDia,
        String abertoPor,
        String placa,
        int membros,
        long notas,
        BigDecimal producao,
        BigDecimal valorMedioNota,
        BigDecimal metaDiaria,
        BigDecimal percentualMetaDiaria,
        OffsetDateTime primeiroLancamento,
        OffsetDateTime ultimoLancamento,
        /* A equipe esqueceu o turno aberto e o servidor o encerrou no horário de corte. */
        boolean encerradoAutomaticamente,
        String motivoEncerramento
) {
}
