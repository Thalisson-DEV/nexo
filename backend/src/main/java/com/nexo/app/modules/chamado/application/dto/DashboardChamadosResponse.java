package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.GranularidadeDashboard;
import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payload completo e consolidado do Dashboard Gerencial da Central de Chamados.
 */
public record DashboardChamadosResponse(
        OffsetDateTime inicio,
        OffsetDateTime fim,
        OffsetDateTime atualizadoEm,
        PeriodoChamadoDashboard periodo,
        QualidadeDadosChamadoDashboard qualidadeDados,

        @Schema(description = "Indicadores principais comparativos (totalChamados, chamadosResolvidos, taxaResolucao, taxaSlaResolucao, taxaSlaPrimeiraResposta, backlogTotal, chamadosCriticos)")
        Map<String, IndicadorComparativoChamado> indicadores,

        // Totais e contagens
        long totalChamados,
        long totalAbertos,
        long totalEmAtendimento,
        long totalAguardandoSolicitante,
        long totalResolvidos,
        long totalFechados,
        long totalCancelados,
        long totalPendentes,
        long totalCriticos,
        long totalReabertos,

        // Percentuais e taxas
        BigDecimal taxaReabertura,
        BigDecimal taxaResolucao,
        BigDecimal taxaSlaPrimeiraRespostaPercentual,
        BigDecimal taxaSlaResolucaoPercentual,

        // Tempos de ciclo
        BigDecimal tempoMedianoPrimeiraRespostaHoras,
        BigDecimal tempoP90PrimeiraRespostaHoras,
        BigDecimal tempoMedianoResolucaoHoras,
        BigDecimal tempoP90ResolucaoHoras,
        BigDecimal tempoMedioResolucaoHoras,

        // Prazos e SLA
        long totalPrimeiraRespostaNoPrazo,
        long totalPrimeiraRespostaVencida,
        long totalResolucaoNoPrazo,
        long totalResolucaoVencida,

        // Distribuições
        Map<StatusChamado, Long> totalPorStatus,
        Map<PrioridadeChamado, Long> totalPorPrioridade,
        List<VolumePrioridadeChamadoItem> volumePorPrioridade,
        List<FaixaBacklogChamado> backlogPorIdade,

        // Desempenhos e rankings
        List<TopicoDesempenhoItem> desempenhoPorTopico,
        List<EquipeDesempenhoItem> desempenhoPorEquipe,
        List<AtendenteDesempenhoItem> desempenhoPorAtendente,
        List<RankingSolicitanteItem> rankingSolicitantes,

        // Visão gerencial e insights
        List<PrioridadeChamadoDashboard> prioridades,

        // Série temporal
        GranularidadeDashboard granularidade,
        List<SerieTemporalChamadoItem> serieTemporal
) {}
