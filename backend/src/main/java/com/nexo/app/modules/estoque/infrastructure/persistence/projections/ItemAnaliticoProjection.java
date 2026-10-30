package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

import java.math.BigDecimal;
import java.util.UUID;

/** Item de uma solicitação do painel, para a exportação detalhada (um material por linha). */
public interface ItemAnaliticoProjection {
    UUID getIdSolicitacao();
    String getCodigo();
    String getDescricao();
    String getUnidade();
    String getNumeroSerie();
    BigDecimal getQuantidadeSolicitada();
    BigDecimal getQuantidadeAtendida();
    String getMotivoPendencia();
    String getJustificativaPendencia();
}
