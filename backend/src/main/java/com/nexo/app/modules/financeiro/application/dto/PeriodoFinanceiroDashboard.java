package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Janela consultada e a janela imediatamente anterior usada nas comparações.
 *
 * <p>Quando o recorte é um mês de calendário fechado, a comparação é o mês
 * anterior inteiro — e não "os mesmos 31 dias para trás", que faria fevereiro
 * parecer pior que janeiro só por ter menos dias.</p>
 */
public record PeriodoFinanceiroDashboard(
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String timezone,

        @Schema(description = "Início da janela usada como base de comparação")
        OffsetDateTime comparacaoInicio,

        @Schema(description = "Fim da janela usada como base de comparação")
        OffsetDateTime comparacaoFim,

        @Schema(description = "Dias corridos cobertos pelo recorte, com as bordas incluídas")
        long diasNoPeriodo
) {
}
