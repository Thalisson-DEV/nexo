package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

import java.math.BigDecimal;

public interface MaterialAnaliticoProjection {
    String getContexto();
    String getSentido();
    String getCodigo();
    String getDescricao();
    String getUnidade();
    Long getSolicitacoes();
    BigDecimal getQuantidadeSolicitada();
    BigDecimal getQuantidadeAtendida();
}
