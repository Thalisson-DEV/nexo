package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

public interface DashboardSolicitacaoProjection {
    Long getTotalSolicitacoes();
    Long getTotalPendentes();
    Long getTotalAprovadas();
    Long getTotalRejeitadas();
    Long getTotalEntregues();
    Long getTotalEntreguesParciais();
    Long getTotalEstornados();
    Double getTempoMedioAtendimentoHoras();
    Double getTempoMedioAprovacaoHoras();
    Double getMediaSolicitacoesPorDia();
    Double getPercentualPendentes();
    Double getPercentualAprovadas();
    Double getPercentualRejeitadas();
    Double getPercentualEntregues();
    Double getPercentualEntreguesParciais();
    Double getPercentualEstornados();
    Double getIndiceRejeicao();
    Double getIndiceSucessoEntrega();
    Long getTotalItensSolicitados();
    Double getMediaItensPorSolicitacao();
}
