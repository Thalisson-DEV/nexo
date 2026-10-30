package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.modules.execucao.application.dto.LancamentoDoTurno;
import com.nexo.app.modules.execucao.domain.StatusTurno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * O que situa uma nota para quem acompanha a equipe: o turno em que foi lançada e as
 * demais notas dele, o dia da equipe contra a meta, o valor médio das notas da equipe
 * nos dias anteriores e o valor de cada ação no contrato.
 */
public record ContextoExecucaoResponse(
        UUID idExecucao,
        /* Nulo se o turno não for encontrado. */
        Turno turno,
        /* Todas as notas do turno, em ordem de lançamento, incluindo esta. */
        List<LancamentoDoTurno> lancamentosDoTurno,
        DiaDaEquipe diaDaEquipe,
        ReferenciaEquipe referenciaEquipe,
        List<ValorAcao> valoresPorAcao
) {

    public record Turno(UUID id, StatusTurno status, OffsetDateTime dataInicio, OffsetDateTime dataFim) {
    }

    /** Notas concluídas da equipe no dia do lançamento. */
    public record DiaDaEquipe(
            LocalDate data,
            long notas,
            BigDecimal producao,
            BigDecimal metaDiaria,
            BigDecimal percentualMetaDiaria
    ) {
    }

    /** Notas concluídas da equipe nos {@code dias} anteriores ao dia do lançamento. */
    public record ReferenciaEquipe(int dias, long notas, BigDecimal valorMedioNota) {
    }

    /** Valor de referência da ação no contrato da equipe, na data do lançamento. */
    public record ValorAcao(UUID idAcao, BigDecimal valor) {
    }
}
