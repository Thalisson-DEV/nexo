package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Quanto tempo cada etapa consome, em dias corridos.
 *
 * <p>A média sozinha esconde o caso ruim: um pedido esquecido por 90 dias some no
 * meio de dezenas decididos no mesmo dia. Por isso cada etapa vem também com a
 * mediana (o caso típico) e o P90 (o compromisso que se cumpre em 9 de cada 10
 * vezes) — é este último que serve para prometer prazo a alguém.</p>
 *
 * <p>O tempo de preenchimento é medido de propósito: um rascunho que leva duas
 * semanas para ser submetido não é problema do aprovador, e cobrar a demora dele
 * apontaria o dedo para a pessoa errada.</p>
 */
public record CicloSolicitacaoDashboard(
        @Schema(description = "Da abertura do rascunho até a submissão")
        BigDecimal mediaDiasAteSubmissao,
        BigDecimal medianaDiasAteSubmissao,
        BigDecimal p90DiasAteSubmissao,

        @Schema(description = "Da submissão até a decisão, nas já decididas")
        BigDecimal mediaDiasAteDecisao,
        BigDecimal medianaDiasAteDecisao,
        BigDecimal p90DiasAteDecisao,

        @Schema(description = "Ciclo completo: da abertura até a decisão")
        BigDecimal mediaDiasCicloTotal,
        BigDecimal medianaDiasCicloTotal,
        BigDecimal p90DiasCicloTotal,

        @Schema(description = "Da submissão até a recusa, nas solicitações rejeitadas")
        BigDecimal mediaDiasAteRejeicao,

        @Schema(description = "Idade média da fila de decisão, desde a submissão")
        BigDecimal idadeMediaFilaDias,

        @Schema(description = "Idade da solicitação mais antiga parada na fila de decisão")
        long idadeMaximaFilaDias,

        @Schema(description = "Idade média dos rascunhos abertos e nunca submetidos")
        BigDecimal idadeMediaRascunhosDias,

        @Schema(description = "Amostra usada nas estatísticas de submissão")
        long amostraSubmissao,

        @Schema(description = "Amostra usada nas estatísticas de decisão")
        long amostraDecisao,

        @Schema(description = "Aprovações e rejeições desfeitas nos pedidos do período: retrabalho de decisão")
        long decisoesDesfeitas
) {
}
