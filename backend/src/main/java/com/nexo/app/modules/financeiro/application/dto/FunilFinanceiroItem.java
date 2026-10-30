package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Uma etapa do funil de autorização das solicitações do período.
 *
 * <p>O funil olha a <b>coorte</b>: todas as solicitações abertas no período,
 * independentemente de quando avançaram. Uma solicitação aprovada conta também
 * como submetida — é assim que "das que abrimos, quantas chegaram ao fim" fica
 * legível, e é por isso que as etapas nunca crescem da esquerda para a
 * direita.</p>
 *
 * <p>Rejeitadas e canceladas aparecem como <b>vazamento</b>, fora da cascata: uma
 * é decisão de quem autoriza, a outra é desistência de quem pediu, e somá-las à
 * sequência principal esconderia de qual dos dois lados o pedido se perdeu.</p>
 */
public record FunilFinanceiroItem(
        @Schema(description = "ABERTAS, SUBMETIDAS, APROVADAS, REJEITADAS ou CANCELADAS")
        String etapa,
        String rotulo,
        long quantidade,

        @Schema(description = "Valor solicitado na etapa; nas APROVADAS, o valor autorizado")
        BigDecimal valor,

        @Schema(description = "Percentual sobre o topo do funil")
        BigDecimal percentualDoTopo,

        @Schema(description = "Percentual sobre a etapa imediatamente anterior; null no topo")
        BigDecimal percentualDaEtapaAnterior,

        @Schema(description = "true quando a etapa é perda e não faz parte da cascata")
        boolean vazamento
) {
}
