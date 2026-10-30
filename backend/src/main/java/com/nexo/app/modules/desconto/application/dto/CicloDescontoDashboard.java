package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Quanto tempo cada etapa consome, em dias corridos.
 *
 * <p>A média sozinha esconde o caso ruim: um termo esquecido por 90 dias some no meio
 * de dezenas resolvidos no mesmo dia. Por isso cada etapa vem também com a mediana
 * (o caso típico) e o P90 (o compromisso que se consegue cumprir em 9 de cada 10
 * vezes) — é este último que serve para prometer prazo a alguém.</p>
 */
public record CicloDescontoDashboard(
        @Schema(description = "Da solicitação até a validação, nos termos já validados")
        BigDecimal mediaDiasAteValidacao,
        BigDecimal medianaDiasAteValidacao,
        BigDecimal p90DiasAteValidacao,

        @Schema(description = "Da validação até a baixa, nos termos já baixados")
        BigDecimal mediaDiasAteBaixa,
        BigDecimal medianaDiasAteBaixa,
        BigDecimal p90DiasAteBaixa,

        @Schema(description = "Ciclo completo: da solicitação até a baixa")
        BigDecimal mediaDiasCicloTotal,
        BigDecimal medianaDiasCicloTotal,
        BigDecimal p90DiasCicloTotal,

        @Schema(description = "Da solicitação até a recusa, nos termos rejeitados")
        BigDecimal mediaDiasAteRejeicao,

        @Schema(description = "Idade média das pendências, contada desde a entrada na etapa atual")
        BigDecimal idadeMediaPendentesDias,

        @Schema(description = "Idade da pendência mais antiga em aberto")
        long idadeMaximaPendenteDias,

        @Schema(description = "Amostra usada nas estatísticas de validação")
        long amostraValidacao,

        @Schema(description = "Amostra usada nas estatísticas de baixa")
        long amostraBaixa
) {
}
