package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma linha de ranking, usada por todas as dimensões simples do dashboard — base,
 * centro de custo, obra, fornecedor, tipo de despesa, formulário e solicitante.
 *
 * <p>Traz volume e dinheiro lado a lado porque as duas ordenações discordam com
 * frequência: o fornecedor mais <em>frequente</em> raramente é o mais
 * <em>caro</em>, e tratar os dois como a mesma pergunta leva a atacar o problema
 * errado.</p>
 *
 * <p>As duas últimas colunas ligam o ranking ao repositório documental: um grupo
 * que aprova muito e anexa pouco é onde a despesa está ficando sem lastro, e essa
 * leitura só existe quando os dois números aparecem na mesma linha.</p>
 *
 * <p>{@code nome} pode vir nulo quando o cadastro de origem não foi encontrado — é
 * dado a exibir vazio, nunca motivo de erro na tela. Já a {@code chave} nunca é
 * nula: é ela que o drilldown devolve ao filtro, e nas dimensões que não são
 * cadastro (tipo de despesa) ela é o único identificador que existe.</p>
 */
public record RankingFinanceiroItem(
        @Schema(description = "Identificador para o filtro: o UUID nas dimensões de cadastro, "
                + "o nome da constante nas enumeradas")
        String chave,

        @Schema(description = "Presente apenas nas dimensões que apontam para um cadastro")
        UUID id,
        String nome,

        long quantidade,
        BigDecimal percentualQuantidade,

        BigDecimal valorSolicitado,
        BigDecimal valorAprovado,

        @Schema(description = "Percentual do valor aprovado do grupo sobre o aprovado do período")
        BigDecimal percentualValor,

        long aprovadas,
        long rejeitadas,
        long pendentes,

        @Schema(description = "Aprovadas sobre as que já têm desfecho; 0 sem nenhuma decisão")
        BigDecimal taxaAprovacao,
        BigDecimal ticketMedio,

        @Schema(description = "Documentos no repositório vindos das solicitações do grupo")
        long documentos,

        @Schema(description = "Percentual das aprovadas do grupo com nota, boleto ou recibo anexado")
        BigDecimal percentualComDocumentoFiscal
) {
}
