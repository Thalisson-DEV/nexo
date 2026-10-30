package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Meta de faturamento cadastrada. Os campos {@code janelaAtual*}, {@code diasUteisJanelaAtual}
 * e {@code metaDiariaAtual} descrevem a janela vigente hoje e ficam nulos fora da vigência.
 */
public record MetaFaturamentoResponse(
        UUID id,
        UUID idEquipe,
        String codigoEquipe,
        String processo,
        String baseOperacional,
        BigDecimal valor,
        PeriodicidadeMetaFaturamento periodicidade,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        String descricao,
        boolean ativo,
        LocalDate janelaAtualInicio,
        LocalDate janelaAtualFim,
        Integer diasUteisJanelaAtual,
        BigDecimal metaDiariaAtual
) {
}
