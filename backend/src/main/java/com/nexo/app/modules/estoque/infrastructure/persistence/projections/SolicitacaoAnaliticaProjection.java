package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linha do painel analítico: a solicitação com marcos de tempo e quantidades agregadas dos itens.
 * Instantes vêm em epoch (ms) e a data prevista em ISO, para não depender da conversão de tipos nativos.
 */
public interface SolicitacaoAnaliticaProjection {
    UUID getId();
    String getContexto();
    String getStatus();
    String getTipoTecnico();
    String getTipoObra();
    String getOcorrencia();
    UUID getIdOcorrencia();
    UUID getIdObra();
    String getStatusSeparacao();
    Long getDataSolicitacao();
    Long getDataVerificacao();
    Long getDataEntrega();
    String getDataPrevista();
    Long getSeparacaoIniciadaEm();
    Long getSeparacaoConcluidaEm();
    Long getEncerradaEm();
    String getEstoqueOrigem();
    String getEstoqueDestino();
    UUID getIdAlmoxarifado();
    String getAlmoxarifado();
    UUID getIdEquipe();
    UUID getIdEquipeSaque();
    Boolean getSaqueForaEquipe();
    String getMotivoRejeicao();
    String getMotivoPendencia();
    String getMotivosPendenciaItens();
    Long getItensComPendencia();
    Boolean getTemReserva();
    Long getItens();
    BigDecimal getQuantidadeSolicitada();
    BigDecimal getQuantidadeAtendida();
    Boolean getTemSaldoPendente();
    Boolean getAnteriorAoPeriodo();
}
