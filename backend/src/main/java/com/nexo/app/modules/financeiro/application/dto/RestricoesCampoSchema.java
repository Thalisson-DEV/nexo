package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Limites de preenchimento de um campo. São publicados para validação imediata na
 * tela e reaplicados no servidor — a validação do cliente é conveniência, não
 * garantia.
 */
@Schema(description = "Limites de preenchimento do campo")
public record RestricoesCampoSchema(

        @Schema(description = "Máximo de caracteres em campos de texto", example = "500")
        Integer tamanhoMaximo,

        @Schema(description = "Menor valor aceito em campos numéricos", example = "0.01")
        BigDecimal valorMinimo,

        @Schema(description = "Maior valor aceito em campos numéricos", example = "100000.00")
        BigDecimal valorMaximo
) {

    /** Converte os limites do catálogo, devolvendo nulo quando não há nenhum. */
    public static RestricoesCampoSchema de(CampoCanonico.Restricoes restricoes) {
        if (restricoes == null || restricoes.vazio()) {
            return null;
        }
        return new RestricoesCampoSchema(
                restricoes.tamanhoMaximo(), restricoes.valorMinimo(), restricoes.valorMaximo());
    }
}
