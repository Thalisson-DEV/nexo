package com.nexo.app.modules.dashboard.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Situação dos turnos das equipes num dia: quem abriu, com que composição, quanto
 * já lançou e quem está há muito tempo sem lançar.
 *
 * <p>Uma equipe pode ter mais de um turno no dia (fechou e reabriu); os números
 * somam todos, e os dados de composição vêm do turno mais recente. Os lançamentos
 * contados são os feitos no dia, mesmo que o turno tenha começado no dia anterior.</p>
 */
public record DashboardTurnosResponse(
        LocalDate data,
        boolean hoje,
        boolean diaUtil,
        OffsetDateTime geradoEm,
        /* A partir de quantos minutos sem lançamento uma equipe em turno é destacada. */
        int limiteSemLancamentoMinutos,
        Resumo resumo,
        /* Notas e produção do dia por tipo de serviço. */
        List<ProducaoPorTipoServico> tiposServico,
        List<Equipe> equipes
) {

    public record Resumo(
            int equipesAtivas,
            int emTurno,
            int encerrados,
            int semTurno,
            int semLancamentoRecente,
            int turnosAbertosDeOutroDia,
            long notas,
            BigDecimal producao,
            int equipesComMeta,
            BigDecimal metaDiaria,
            BigDecimal producaoEquipesComMeta,
            BigDecimal percentualMetaDiaria
    ) {
    }

    public record Equipe(
            UUID idEquipe,
            String codigo,
            String baseOperacional,
            String processo,
            SituacaoTurno situacao,
            /* Turno mais recente do dia (o aberto, se houver). */
            UUID idTurno,
            int quantidadeTurnos,
            OffsetDateTime abertura,
            OffsetDateTime fechamento,
            long minutosEmTurno,
            boolean abertoDesdeOutroDia,
            String abertoPor,
            String placa,
            List<String> membros,
            long notas,
            BigDecimal producao,
            BigDecimal valorMedioNota,
            BigDecimal metaDiaria,
            BigDecimal percentualMetaDiaria,
            OffsetDateTime primeiroLancamento,
            OffsetDateTime ultimoLancamento,
            /* Só para equipe em turno no dia corrente: minutos desde o último lançamento (ou da abertura). */
            Long minutosSemLancamento,
            boolean semLancamentoRecente,
            List<Lancamento> lancamentos,
            List<ProducaoPorTipoServico> tiposServico
    ) {
    }

    /* {@code tipoServico} é a descrição do tipo, nula quando não informado. */
    public record Lancamento(UUID idExecucao, OffsetDateTime dataLancamento, BigDecimal valor, String tipoServico) {
    }

    public enum SituacaoTurno {
        EM_TURNO,
        ENCERRADO,
        SEM_TURNO
    }
}
