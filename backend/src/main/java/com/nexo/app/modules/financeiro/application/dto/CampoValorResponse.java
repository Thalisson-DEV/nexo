package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Valor preenchido num campo, em duas leituras.
 *
 * <p>{@code valor} é o dado bruto e tipado, pronto para recarregar o formulário
 * em edição. {@code exibicao} é o texto legível já resolvido pelo servidor — o
 * nome do fornecedor em vez do UUID, o rótulo da opção em vez da constante. Assim
 * a listagem e o detalhe são renderizáveis sem nenhuma chamada extra.</p>
 */
@Schema(description = "Valor de um campo, no formato bruto e no formato legível")
public record CampoValorResponse(

        @Schema(description = "Valor tipado, para recarregar o formulário")
        Object valor,

        @Schema(description = "Texto legível já resolvido", example = "Auto Peças Silva")
        String exibicao
) {

    public static CampoValorResponse de(Object valor, String exibicao) {
        return new CampoValorResponse(valor, exibicao);
    }
}
