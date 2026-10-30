package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Regra de visibilidade condicional de um campo.
 *
 * <p>O servidor aplica exatamente esta mesma regra ao validar o payload: campo
 * oculto não é cobrado como obrigatório nem é gravado. O frontend pode confiar
 * nela para mostrar/esconder sem duplicar lógica.</p>
 */
@Schema(description = "Condição que torna o campo visível")
public record CondicaoSchema(

        @Schema(description = "Chave do campo observado", example = "temNf")
        String campoChave,

        @Schema(description = "Valores do campo observado que exibem este campo", example = "[\"false\"]")
        List<String> valores
) {}
