package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Dados consolidados do dashboard de solicitações")
public record DashboardSolicitacaoResponse(
    @Schema(description = "Total geral de solicitações no período")
    long totalSolicitacoes,

    @Schema(description = "Total de solicitações pendentes")
    long totalPendentes,

    @Schema(description = "Total de solicitações aprovadas")
    long totalAprovadas,

    @Schema(description = "Total de solicitações rejeitadas")
    long totalRejeitadas,

    @Schema(description = "Total de solicitações entregues")
    long totalEntregues,

    @Schema(description = "Total de solicitações entregues parcialmente")
    long totalEntreguesParciais,

    @Schema(description = "Total de solicitações estornadas")
    long totalEstornadas,

    @Schema(description = "Tempo médio de atendimento (da aprovação até entrega final) em horas")
    Double tempoMedioAtendimentoHoras,

    @Schema(description = "Tempo médio de aprovação (da solicitação até verificação) em horas")
    Double tempoMedioAprovacaoHoras,

    @Schema(description = "Média de solicitações por dia")
    Double mediaSolicitacoesPorDia,

    @Schema(description = "Percentual de solicitações pendentes")
    Double percentualPendentes,

    @Schema(description = "Percentual de solicitações aprovadas")
    Double percentualAprovadas,

    @Schema(description = "Percentual de solicitações rejeitadas")
    Double percentualRejeitadas,

    @Schema(description = "Percentual de solicitações entregues")
    Double percentualEntregues,

    @Schema(description = "Percentual de solicitações entregues parcialmente")
    Double percentualEntreguesParciais,

    @Schema(description = "Percentual de solicitações estornadas")
    Double percentualEstornadas,

    @Schema(description = "Índice de rejeição (Total Rejeitadas / Total)")
    Double indiceRejeicao,

    @Schema(description = "Índice de sucesso na entrega (Total Entregues / Total)")
    Double indiceSucessoEntrega,

    @Schema(description = "Total de itens solicitados")
    long totalItensSolicitados,

    @Schema(description = "Média de tipos de materiais por solicitação")
    Double mediaItensPorSolicitacao,

    @Schema(description = "Top 5 materiais mais solicitados")
    List<TopMaterialItem> topMateriais,

    @Schema(description = "Top 5 estoques de destino que mais solicitam")
    List<TopEstoqueItem> topEstoquesDestino,

    @Schema(description = "Top 5 motivos de rejeição mais frequentes")
    List<TopMotivoItem> topMotivosRejeicao,

    @Schema(description = "Fila tecnica segmentada, separada das metricas comerciais legadas")
    List<SegmentoSolicitacaoTecnicaItem> segmentosTecnicos
) {}
