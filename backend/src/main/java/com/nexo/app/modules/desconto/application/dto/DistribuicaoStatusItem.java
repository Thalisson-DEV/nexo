package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.StatusDesconto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Quantidade e valor parados em cada status. Os dois percentuais existem porque
 * costumam divergir: poucos termos de valor alto pesam mais no caixa do que muitos
 * termos pequenos, e é essa diferença que indica onde vale insistir.
 */
public record DistribuicaoStatusItem(
        StatusDesconto status,
        String rotulo,
        long quantidade,

        @Schema(description = "Valor corrente do status: baixado > validado > sugerido")
        BigDecimal valor,
        BigDecimal percentualQuantidade,
        BigDecimal percentualValor
) {
}
