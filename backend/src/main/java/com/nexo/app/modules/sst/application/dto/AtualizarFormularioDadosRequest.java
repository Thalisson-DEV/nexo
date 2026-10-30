package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Atualização apenas dos metadados de um formulário (título/descrição),
 * permitida mesmo após o formulário já ter sido utilizado em inspeções.
 */
public record AtualizarFormularioDadosRequest(
        @NotBlank(message = "O título do formulário é obrigatório")
        String titulo,

        String descricao
) {}
