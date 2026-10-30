package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

public interface ResumoPeriodoSolicitacaoProjection {
    String getContexto();
    Long getTotal();
    Long getConcluidas();
    Long getRejeitadas();
    Double getCicloMedioHoras();
    Double getAprovacaoMediaHoras();
}
