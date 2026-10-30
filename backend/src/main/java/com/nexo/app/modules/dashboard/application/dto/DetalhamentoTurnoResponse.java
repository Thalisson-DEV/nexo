package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Relatório de um turno: o turno, a composição registrada na abertura, as notas
 * lançadas nele, os indicadores de ritmo e meta e a referência dos turnos anteriores
 * da equipe.
 */
public record DetalhamentoTurnoResponse(
        TurnoResponse turno,
        Composicao composicao,
        List<ExecucaoComercialResponse> execucoes,
        Indicadores indicadores,
        Historico historicoEquipe
) {

    /** Retrato confirmado na abertura do turno — não a composição atual da equipe. */
    public record Composicao(String abertoPor, String placa, List<String> membros) {
    }

    /**
     * Números do turno. Produção e notas contam só lançamentos concluídos. Os tempos
     * vão até o fechamento ou, com o turno aberto, até {@code calculadoEm}.
     */
    public record Indicadores(
            OffsetDateTime calculadoEm,
            boolean emAndamento,
            /* Aberto e iniciado antes de hoje: provável turno esquecido. */
            boolean abertoDesdeOutroDia,
            long duracaoMinutos,
            long notas,
            BigDecimal producao,
            BigDecimal valorMedioNota,
            /* Produção dividida pelas horas de turno; nula em turno de menos de um minuto. */
            BigDecimal producaoPorHora,
            /* Meta da equipe no dia da abertura; nula sem meta ou em dia não útil. */
            BigDecimal metaDiaria,
            BigDecimal percentualMetaDiaria,
            Long minutosAtePrimeiroLancamento,
            /* Do último lançamento ao fechamento (ou até agora, com o turno aberto). */
            Long minutosAposUltimoLancamento,
            /* Maior trecho sem lançar, contando da abertura e até o fim do turno. */
            Long maiorIntervaloSemLancamentoMinutos,
            int lancamentosSemLocalizacao
    ) {
    }

    /**
     * Médias dos turnos encerrados da equipe nos {@code dias} anteriores à abertura
     * deste turno — a régua para dizer se o turno foi bom ou ruim para a equipe.
     * As médias são nulas quando não há turno anterior no período.
     */
    public record Historico(
            int dias,
            int turnos,
            BigDecimal mediaNotasPorTurno,
            BigDecimal mediaProducaoPorTurno,
            Long mediaDuracaoMinutos,
            BigDecimal valorMedioNota
    ) {
    }
}
