package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Um número ao lado do que ele era na janela anterior.
 *
 * <p>A {@code definicao} viaja junto de propósito: um indicador cuja regra de cálculo
 * só existe na cabeça de quem o programou vira discussão na reunião. Aqui a tela
 * exibe a definição no mesmo lugar em que exibe o valor.</p>
 */
public record IndicadorComparativoDesconto(
        BigDecimal valor,
        BigDecimal anterior,

        @Schema(description = "Variação sobre o anterior; 0 quando não há base de comparação")
        BigDecimal variacaoPercentual,

        @Schema(description = "Sentido em que a variação é boa: SUBIR, DESCER ou NEUTRO")
        String direcaoDesejada,

        String definicao
) {
}
