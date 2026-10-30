package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard executivo focado em DESVIOS (não-conformidades): totais por gravidade
 * e status, percentuais, médias, prazos, reincidência e rankings.
 */
public record DashboardDesviosResponse(
        OffsetDateTime inicio,
        OffsetDateTime fim,

        // Totais
        @Schema(description = "Todos os desvios apontados nas inspeções da coorte, independentemente do status atual")
        long totalDesvios,
        @Schema(description = "Desvios ABERTO ou EM_TRATAMENTO da coorte consultada")
        long totalPendentes,
        long totalAbertos,
        long totalEmTratamento,
        long totalResolvidos,
        long totalCriticos,               // ALTA + CRITICA
        long totalVencidos,               // prazo < hoje e não resolvido
        long totalNoPrazo,
        long totalSemPrazo,
        long totalColaboradoresEnvolvidos,
        long totalColaboradoresReincidentes,
        long totalInspecoesComDesvio,

        // Distribuições
        Map<GravidadeDesvio, Long> totalPorGravidade,
        Map<StatusDesvio, Long> totalPorStatus,

        // Percentuais
        BigDecimal taxaResolucao,
        BigDecimal percentualCriticos,
        BigDecimal percentualVencidos,
        BigDecimal percentualReincidencia,

        // Médias
        @Schema(deprecated = true, description = "Métrica individual mantida apenas para compatibilidade")
        BigDecimal mediaDesviosPorColaborador,
        BigDecimal mediaDesviosPorInspecao,
        @Schema(deprecated = true, description = "Use tempoMedianoResolucaoDias e tempoP90ResolucaoDias")
        BigDecimal tempoMedioResolucaoDias,

        // Rankings
        @Schema(deprecated = true, description = "Ranking individual restrito; prefira itens, processos e locais")
        List<RankingItem> rankingColaboradores,
        List<RankingItem> rankingPerguntas,       // itens do checklist que mais reprovam
        @Schema(deprecated = true, description = "Use exposicaoPorProcesso")
        List<RankingItem> rankingProcessos,
        List<RankingItem> rankingProcessosInspetores,
        @Schema(deprecated = true, description = "Use exposicaoPorEquipe")
        List<RankingItem> rankingEquipes,
        @Schema(description = "Distribuição bruta por base; prefira exposicaoPorBaseOperacional para comparação gerencial")
        List<RankingItem> rankingBasesOperacionais,
        List<RankingItem> rankingInspetores,

        // Pendências acionáveis por dimensão gerencial
        @Schema(description = "Pendências agrupadas por processo da inspeção, com abertos, em tratamento, vencidos e críticos")
        List<PendenciaAgrupadaResponse> pendenciasPorProcesso,
        @Schema(description = "Pendências agrupadas por processo do inspetor, com abertos, em tratamento, vencidos e críticos")
        List<PendenciaAgrupadaResponse> pendenciasPorProcessoInspetor,
        @Schema(description = "Pendências agrupadas por base operacional")
        List<PendenciaAgrupadaResponse> pendenciasPorBaseOperacional,
        @Schema(description = "Pendências agrupadas por equipe")
        List<PendenciaAgrupadaResponse> pendenciasPorEquipe,

        // Detalhamento por colaborador
        @Schema(deprecated = true, description = "Sem semântica gerencial estável; mantido apenas para compatibilidade")
        List<ColaboradorDesvioKpi> desempenhoPorColaborador,

        // Série temporal legada
        @Schema(deprecated = true, description = "Série diária esparsa mantida para compatibilidade; use tendenciaDesvios")
        List<SerieTemporalItem> desviosPorDia,

        // Contrato gerencial evoluído
        PeriodoDashboard periodo,
        OffsetDateTime atualizadoEm,
        QualidadeDadosDashboard qualidadeDados,
        @Schema(description = "Comparativos gerais e operacionais; totalDesvios é a leitura principal de ocorrências")
        Map<String, IndicadorComparativo> indicadores,
        BigDecimal tempoMedianoResolucaoDias,
        BigDecimal tempoP90ResolucaoDias,
        List<FaixaBacklog> backlogPorIdade,
        @Schema(description = "Volume de desvios por gravidade da coorte, com percentual de participação e comparação com período anterior")
        List<VolumePorGravidade> volumePorGravidade,
        List<ExposicaoNormalizadaItem> exposicaoPorProcesso,
        List<ExposicaoNormalizadaItem> exposicaoPorProcessoInspetor,
        List<ExposicaoNormalizadaItem> exposicaoPorEquipe,
        List<ExposicaoNormalizadaItem> exposicaoPorBaseOperacional,
        List<ItemCriticoRecorrente> itensCriticosRecorrentes,
        List<PrioridadeDashboard> prioridades,
        @Schema(deprecated = true, description = "Série operacional diária de fluxo e backlog; use tendenciaDesvios para a ocorrência histórica")
        List<SerieDesviosItem> serieDesvios,

        @Schema(description = "Granularidade da tendência geral; automática quando omitida no filtro")
        GranularidadeDashboard granularidade,
        @Schema(description = "Tendência principal de todos os desvios pela data em que foram apontados, sem excluir resolvidos")
        List<TendenciaDesviosItem> tendenciaDesvios
) {}
