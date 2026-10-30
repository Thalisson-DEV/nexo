package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Quantidade e valor parados em cada status. Os dois percentuais existem porque
 * costumam divergir: poucas solicitações de valor alto pesam mais no caixa do que
 * muitas pequenas, e é essa diferença que indica onde vale insistir.
 */
public record DistribuicaoStatusFinanceiroItem(
        StatusSolicitacao status,
        String rotulo,
        long quantidade,

        @Schema(description = "Valor vigente do status: o aprovado quando há decisão, senão o solicitado")
        BigDecimal valor,
        BigDecimal percentualQuantidade,
        BigDecimal percentualValor
) {
}
