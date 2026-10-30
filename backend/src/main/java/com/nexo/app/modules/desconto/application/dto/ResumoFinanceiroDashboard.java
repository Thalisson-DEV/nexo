package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * O dinheiro em cada etapa do ciclo e o que se perde entre elas.
 *
 * <p>Um termo passa por três valores diferentes — o somatório dos itens lançados,
 * o que a validação reconheceu e o que de fato foi descontado em folha. Comparar os
 * três é o que responde a pergunta que a gestão realmente faz: <em>de tudo que foi
 * pedido, quanto virou desconto?</em> As duas diferenças recebem nome próprio porque
 * são decididas por pessoas distintas — a glosa é da validação; o ajuste, da baixa.</p>
 */
public record ResumoFinanceiroDashboard(
        @Schema(description = "Somatório dos itens de todos os descontos do período")
        BigDecimal valorSolicitado,

        @Schema(description = "Somatório do valor reconhecido na validação (validados e baixados)")
        BigDecimal valorValidado,

        @Schema(description = "Somatório do que foi efetivamente descontado em folha")
        BigDecimal valorBaixado,
        BigDecimal valorBaixadoPeriodoAnterior,
        BigDecimal variacaoValorBaixado,

        @Schema(description = "Valor solicitado dos termos recusados")
        BigDecimal valorRejeitado,

        @Schema(description = "Valor corrente dos termos ainda pendentes (baixado > validado > sugerido)")
        BigDecimal valorEmAberto,

        @Schema(description = "Solicitado menos validado, nos termos que chegaram à validação")
        BigDecimal glosaValidacao,
        BigDecimal percentualGlosaValidacao,

        @Schema(description = "Validado menos baixado, nos termos já baixados")
        BigDecimal ajusteBaixa,
        BigDecimal percentualAjusteBaixa,

        @Schema(description = "Baixado sobre solicitado — quanto do pedido virou desconto real")
        BigDecimal taxaRecuperacao,

        BigDecimal ticketMedioSolicitado,
        BigDecimal ticketMedioBaixado,

        @Schema(description = "Maior valor solicitado em um único termo no período")
        BigDecimal maiorValorSolicitado,

        @Schema(description = "Valor baixado dividido pelas parcelas — o impacto mensal em folha")
        BigDecimal valorMedioParcela
) {
}
