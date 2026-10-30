package com.nexo.app.modules.notificacao.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Correção de texto de um comunicado já publicado. Só o que se pode consertar sem
 * alterar o que o comunicado já significou para quem o leu.
 */
public record CorrigirTextoComunicadoRequest(

        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @Size(max = 300, message = "O resumo deve ter no máximo 300 caracteres.")
        String resumo,

        @NotBlank(message = "O conteúdo é obrigatório.")
        String conteudo
) {
}
