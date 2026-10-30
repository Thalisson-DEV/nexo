package com.nexo.app.modules.dashboard.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Produção das equipes num período: quanto foi lançado, como isso se compara à meta
 * de cada equipe e se o ritmo atual fecha a meta.
 *
 * <p>O "ritmo" compara a produção com a meta esperada até o último dia útil já
 * encerrado (o dia corrente ainda está em andamento e não entra na conta). A
 * projeção soma à produção atual o ritmo médio desses dias úteis aplicado aos dias
 * úteis que ainda faltam no período.</p>
 */
public record DashboardOperacionalResponse(
        LocalDate inicio,
        LocalDate fim,
        OffsetDateTime geradoEm,
        int diasUteisPeriodo,
        int diasUteisEncerrados,
        Resumo resumo,
        List<ProducaoDia> producaoDiaria,
        /* Notas e produção do período por tipo de serviço. */
        List<ProducaoPorTipoServico> tiposServico,
        List<Equipe> equipes
) {

    public record Resumo(
            BigDecimal producao,
            BigDecimal producaoPeriodoAnterior,
            /* Variação percentual contra o período anterior de mesmo tamanho; nula sem base. */
            BigDecimal variacaoPercentual,
            long notas,
            BigDecimal valorMedioNota,
            int equipesAtivas,
            int equipesComProducao,
            int equipesComMeta,
            /* Soma das metas proporcionais das equipes com meta cadastrada. */
            BigDecimal metaPeriodo,
            /* Produção apenas das equipes com meta — o numerador comparável a metaPeriodo. */
            BigDecimal producaoEquipesComMeta,
            BigDecimal percentualMeta,
            BigDecimal projecaoEquipesComMeta,
            int equipesAbaixoDoRitmo
    ) {
    }

    public record Equipe(
            UUID idEquipe,
            String codigo,
            String baseOperacional,
            String processo,
            String tipoEquipe,
            String placa,
            BigDecimal producao,
            long notas,
            BigDecimal valorMedioNota,
            long diasComTurno,
            long diasComProducao,
            BigDecimal producaoMediaPorDia,
            BigDecimal metaMensal,
            BigDecimal metaPeriodo,
            BigDecimal metaEsperadaAteAgora,
            BigDecimal percentualMeta,
            BigDecimal projecao,
            SituacaoMeta situacao,
            OffsetDateTime ultimoLancamento,
            List<ProducaoDia> producaoDiaria,
            List<ProducaoPorTipoServico> tiposServico
    ) {
    }

    /**
     * Produção de um dia; o período inteiro vem preenchido, inclusive dias sem lançamento.
     * {@code meta} é a parte da meta mensal que cabe ao dia (zero em dia não útil) e fica
     * nula sem meta cadastrada; na série geral é a soma das equipes com meta.
     */
    public record ProducaoDia(LocalDate data, long notas, BigDecimal valor, BigDecimal meta) {
    }

    public enum SituacaoMeta {
        META_ATINGIDA,
        NO_RITMO,
        ABAIXO_DO_RITMO,
        SEM_META
    }
}
