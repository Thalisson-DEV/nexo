package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Uma faixa da régua de vencimentos das solicitações já autorizadas.
 *
 * <p>Só entra aqui o que foi <b>aprovado</b>: cobrar vencimento de um pedido que
 * ninguém autorizou seria cobrar a data errada da pessoa errada. E só o que ainda
 * não tem comprovante de pagamento no repositório — uma conta já paga não é
 * compromisso, é histórico.</p>
 *
 * <p>A faixa {@code VENCIDO} é a única aberta para trás, e vem primeiro de
 * propósito: ela não é "o próximo balde", é o passivo que já estourou.</p>
 */
public record FaixaVencimentoItem(
        @Schema(description = "'VENCIDO' | '0-7' | '8-15' | '16-30' | '>30'")
        String faixa,
        String rotulo,

        long quantidade,
        BigDecimal valor,

        @Schema(description = "Percentual sobre o valor total a liquidar")
        BigDecimal percentualValor,

        @Schema(description = "Dias até o vencimento do compromisso mais próximo da faixa; "
                + "na faixa VENCIDO, dias de atraso do mais antigo")
        long diasExtremo
) {
}
