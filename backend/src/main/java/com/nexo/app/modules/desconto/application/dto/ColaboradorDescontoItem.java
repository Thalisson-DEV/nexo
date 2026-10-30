package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Descontos atribuídos a um colaborador no período.
 *
 * <p>O campo que muda a conversa é {@code diasDesdeUltimaOcorrencia}: alguém com
 * quatro descontos no mês passado e nenhum desde então já foi tratado; alguém com dois
 * na última semana ainda não. Sem essa coluna, o ranking por quantidade transforma
 * histórico antigo em cobrança nova.</p>
 */
public record ColaboradorDescontoItem(
        UUID idColaborador,
        String nomeColaborador,
        String funcao,
        String baseOperacional,

        long quantidade,
        BigDecimal valorSolicitado,

        @Schema(description = "Valor corrente de cada termo: baixado > validado > sugerido")
        BigDecimal valorEfetivo,

        long baixados,
        long rejeitados,
        long pendentes,

        @Schema(description = "Data de ocorrência mais recente entre os termos do período")
        LocalDate ultimaOcorrencia,
        long diasDesdeUltimaOcorrencia,

        BigDecimal mediaParcelas
) {
}
