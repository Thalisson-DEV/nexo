package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto da série temporal, sempre pela <b>coorte</b>: os termos cuja ocorrência
 * caiu no intervalo, com o desfecho que eles têm hoje.
 *
 * <p>Isto é diferente de "quanto entrou no caixa neste mês". A coorte responde
 * "do que aconteceu em março, quanto virou desconto?" — e é a leitura que não muda de
 * significado quando um termo antigo é baixado com atraso, porque ele continua
 * pertencendo a março.</p>
 *
 * <p>Todos os baldes do período são devolvidos, inclusive os zerados: buraco em série
 * temporal é lido como queda, e não como ausência de dado.</p>
 */
public record SerieDescontoItem(
        @Schema(description = "'2026-08-03' | '2026-08' | '2026'")
        String chave,

        @Schema(description = "'03/08' | 'ago/2026' | '2026'")
        String rotulo,

        @Schema(description = "Primeiro dia do intervalo")
        LocalDate inicio,

        long solicitados,
        BigDecimal valorSolicitado,

        long validados,
        BigDecimal valorValidado,

        long baixados,
        BigDecimal valorBaixado,

        long rejeitados,
        BigDecimal valorRejeitado,

        @Schema(description = "Ainda sem desfecho: SOLICITADO ou VALIDADO")
        long pendentes,

        @Schema(description = "Baixado sobre solicitado no intervalo")
        BigDecimal taxaRecuperacao
) {
}
