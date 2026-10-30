package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Uma fatia do repositório documental — por natureza do documento ou por situação
 * de conferência, conforme a lista em que o item aparece.
 *
 * <p>A chave é o nome da constante e o rótulo é o texto já pronto para a tela: sem
 * ele, cada cliente reescreveria a tradução de {@code COMPROVANTE_PAGAMENTO}, e
 * elas divergiriam.</p>
 */
public record DistribuicaoDocumentoItem(
        @Schema(description = "Nome da constante: tipo de documento ou situação, conforme a lista")
        String chave,
        String rotulo,

        long quantidade,
        BigDecimal percentualQuantidade,

        @Schema(description = "Somatório dos valores informados nos documentos da fatia")
        BigDecimal valor,

        @Schema(description = "Documentos da fatia ainda em aberto na conferência")
        long emAberto
) {
}
