package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.StatusDesconto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Uma faixa de idade do backlog de pendências.
 *
 * <p>A idade é contada desde a entrada na <b>etapa atual</b>, não desde a abertura do
 * termo: um desconto validado ontem depois de dois meses parado é uma pendência nova
 * na fila da baixa, e cobrá-la como se tivesse 60 dias esconderia onde o tempo
 * realmente foi gasto.</p>
 */
public record FaixaEnvelhecimentoItem(
        @Schema(description = "'0-7' | '8-15' | '16-30' | '31-60' | '>60'")
        String faixa,
        int idadeMinimaDias,

        @Schema(description = "null na última faixa, que é aberta")
        Integer idadeMaximaDias,

        long quantidade,
        BigDecimal valor,

        @Schema(description = "Quebra da faixa por status; sempre com todas as chaves pendentes")
        Map<StatusDesconto, Long> porStatus
) {
}
