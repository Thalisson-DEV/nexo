package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Uma etapa do funil de conversão dos termos do período.
 *
 * <p>O funil olha a <b>coorte</b>: todos os termos cuja ocorrência caiu no período,
 * independentemente de quando avançaram. Um termo que já foi baixado conta em todas as
 * etapas anteriores — é assim que "quantos dos que abrimos chegaram ao fim" fica
 * legível, e é por isso que as etapas nunca crescem da esquerda para a direita.</p>
 */
public record FunilEtapaItem(
        @Schema(description = "SOLICITADOS, VALIDADOS, BAIXADOS ou REJEITADOS")
        String etapa,
        String rotulo,
        long quantidade,
        BigDecimal valor,

        @Schema(description = "Percentual sobre o topo do funil")
        BigDecimal percentualDoTopo,

        @Schema(description = "Percentual sobre a etapa imediatamente anterior; null no topo")
        BigDecimal percentualDaEtapaAnterior
) {
}
