package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

import java.math.BigDecimal;
import java.util.UUID;

public interface SegmentoSolicitacaoTecnicaProjection {
    String getContexto();
    String getTipoTecnico();
    String getTipoObra();
    String getOcorrencia();
    UUID getIdObra();
    UUID getIdEquipe();
    String getStatusSeparacao();
    UUID getIdAlmoxarifadoAtendimento();
    Long getTotalSolicitacoes();
    Long getTotalItens();
    BigDecimal getQuantidadeSolicitada();
}
