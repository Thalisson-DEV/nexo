package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Perdas por tipo de equipamento. Aqui a unidade de contagem é o <b>item</b>, não o
 * termo: um mesmo desconto pode lançar um capacete e três luvas, e é o item que diz
 * qual material está sumindo.
 *
 * <p>A taxa de aprovação continua sendo por termo — quem valida decide sobre o termo
 * inteiro. Um tipo que aparece em muitos termos recusados é sinal de critério de
 * lançamento frouxo, não de material caro.</p>
 */
public record TipoEquipamentoDescontoItem(
        UUID id,
        String nome,

        @Schema(description = "Itens lançados deste tipo")
        long itens,

        @Schema(description = "Termos distintos que contêm ao menos um item deste tipo")
        long descontos,

        BigDecimal valorItens,
        BigDecimal percentualValor,
        BigDecimal valorMedioPorItem,

        long descontosBaixados,
        long descontosRejeitados,
        BigDecimal taxaAprovacao
) {
}
