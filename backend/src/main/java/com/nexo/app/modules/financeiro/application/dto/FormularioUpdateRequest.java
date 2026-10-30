package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edição dos metadados de uma variante já existente. */
public record FormularioUpdateRequest(

        @Schema(description = "Título exibido ao solicitante", example = "Solicitação de Compra — Frota")
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @Schema(description = "Texto de apresentação do formulário")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String descricao,

        @Schema(description = "Marca esta variante como a sugerida para o tipo", example = "false")
        boolean padrao
) {}
