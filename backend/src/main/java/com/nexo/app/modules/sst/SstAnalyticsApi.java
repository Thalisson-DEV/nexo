package com.nexo.app.modules.sst;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API publica, somente leitura, para metricas SST consumidas por outros modulos.
 * As definicoes versionadas impedem que um consumidor invente formulas ou SQL.
 */
public interface SstAnalyticsApi {

    MediaMensalInspecoes calcularMediaMensalInspecoes(LocalDate mesReferencia, int quantidadeMeses);

    RankingEquipesDesvios calcularRankingEquipesDesvios(
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            int limite);

    AtingimentoMetasInspecoes calcularAtingimentoMetasInspecoes(
            LocalDate inicioInclusivo,
            LocalDate fimInclusivo,
            int limite);

    CoberturaColaboradores calcularCoberturaColaboradores(
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo);

    Acidentalidade calcularAcidentalidade(
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo);

    record MediaMensalInspecoes(
            String metrica,
            String versao,
            LocalDate mesInicial,
            LocalDate mesFinal,
            int quantidadeMeses,
            long totalInspecoes,
            BigDecimal mediaMensal,
            OffsetDateTime calculadoEm) {
    }

    record RankingEquipesDesvios(
            String metrica,
            String versao,
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            List<EquipeDesvios> equipes,
            long totalInspecoesAmostra,
            OffsetDateTime calculadoEm) {
    }

    record EquipeDesvios(
            UUID idEquipe,
            String nomeEquipe,
            long totalDesvios,
            long totalInspecoes,
            BigDecimal desviosPorCemInspecoes) {
    }

    /**
     * Cumprimento das metas de inspeção no intervalo. A meta de cada colaborador é
     * rateada por dia, de modo que o intervalo consultado não precisa coincidir com
     * a janela de apuração da meta.
     */
    record AtingimentoMetasInspecoes(
            String metrica,
            String versao,
            LocalDate inicioInclusivo,
            LocalDate fimInclusivo,
            boolean possuiMeta,
            BigDecimal metaPeriodo,
            long totalRealizado,
            BigDecimal percentualAtingimento,
            long colaboradoresComMeta,
            long inspetoresNaMeta,
            List<InspetorMeta> inspetores,
            OffsetDateTime calculadoEm) {
    }

    record InspetorMeta(
            UUID idInspetor,
            String nomeInspetor,
            BigDecimal meta,
            long realizado,
            BigDecimal percentualAtingimento,
            boolean atingiuMeta) {
    }

    /**
     * Cobertura do efetivo: colaboradores distintos inspecionados sobre o total de
     * colaboradores ativos.
     */
    /**
     * Acidentalidade do intervalo, agregada no banco.
     *
     * <p>Existe para que a resposta sobre "quantos acidentes tivemos" venha de um
     * número calculado, e não de linhas somadas pelo modelo. Eventos cancelados não
     * entram, como em todo indicador de acidentalidade, e a contagem é pela data da
     * <b>ocorrência</b>.</p>
     */
    record Acidentalidade(
            String metrica,
            String versao,
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            long totalEventos,
            long eventosComLesao,
            long diasPerdidos,
            long eventosComCat,
            List<EventosPorTipo> porTipo,
            OffsetDateTime calculadoEm) {
    }

    record EventosPorTipo(
            String tipo,
            String rotulo,
            long total,
            long diasPerdidos,
            long comCat) {
    }

    record CoberturaColaboradores(
            String metrica,
            String versao,
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            long colaboradoresInspecionados,
            long colaboradoresAtivos,
            BigDecimal percentualCobertura,
            long totalInspecoes,
            OffsetDateTime calculadoEm) {
    }
}
