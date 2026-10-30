package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto da série temporal, sempre pela <b>coorte</b>: as solicitações abertas
 * no intervalo, com o desfecho que elas têm hoje.
 *
 * <p>Isto é diferente de "quanto foi autorizado neste mês". A coorte responde "do
 * que foi pedido em março, quanto foi aprovado?" — e é a leitura que não muda de
 * significado quando um pedido antigo é decidido com atraso, porque ele continua
 * pertencendo a março.</p>
 *
 * <p>Todos os baldes do período são devolvidos, inclusive os zerados: buraco em
 * série temporal é lido como queda, e não como ausência de dado.</p>
 */
public record SerieFinanceiroItem(
        @Schema(description = "'2026-08-03' | '2026-08' | '2026'")
        String chave,

        @Schema(description = "'03/08' | 'ago/2026' | '2026'")
        String rotulo,

        @Schema(description = "Primeiro dia do intervalo")
        LocalDate inicio,

        long abertas,
        BigDecimal valorSolicitado,

        long aprovadas,
        BigDecimal valorAprovado,

        long rejeitadas,
        BigDecimal valorRejeitado,

        long canceladas,

        @Schema(description = "Ainda sem desfecho: RASCUNHO ou SOLICITADO")
        long pendentes,

        @Schema(description = "Valor aprovado sobre o valor solicitado no intervalo")
        BigDecimal taxaAutorizacao
) {
}
