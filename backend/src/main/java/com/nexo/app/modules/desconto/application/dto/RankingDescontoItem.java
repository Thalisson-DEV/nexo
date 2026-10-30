package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma linha de ranking, usada por todas as dimensões simples do dashboard — motivo,
 * base operacional, centro de custo, função e solicitante.
 *
 * <p>Traz volume e dinheiro lado a lado porque as duas ordenações discordam com
 * frequência: o motivo mais <em>frequente</em> raramente é o mais <em>caro</em>, e
 * tratar os dois como a mesma pergunta leva a atacar o problema errado.</p>
 *
 * <p>{@code nome} pode vir nulo quando o cadastro de origem não foi encontrado — é
 * dado a exibir vazio, nunca motivo de erro na tela.</p>
 */
public record RankingDescontoItem(
        UUID id,
        String nome,

        long quantidade,
        BigDecimal percentualQuantidade,

        BigDecimal valorSolicitado,

        @Schema(description = "Valor corrente de cada termo: baixado > validado > sugerido")
        BigDecimal valorEfetivo,
        BigDecimal percentualValor,

        long baixados,
        long rejeitados,
        long pendentes,

        @Schema(description = "Baixados sobre finalizados no grupo; 0 sem nenhum desfecho")
        BigDecimal taxaAprovacao,
        BigDecimal ticketMedio
) {
}
