package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Formulário padrão de abertura; evidências são enviadas após a criação. */
public record AbrirChamadoRequest(
        @NotNull(message = "Selecione um tópico.")
        UUID topicoId,

        @NotBlank(message = "Informe o assunto.")
        @Size(max = 150, message = "O assunto deve ter no máximo 150 caracteres.")
        @Schema(example = "Não consigo acessar o sistema")
        String assunto,

        @NotBlank(message = "Descreva a necessidade ou o problema.")
        @Size(min = 10, max = 5000, message = "A descrição deve ter entre 10 e 5.000 caracteres.")
        @Schema(description = "Inclua o que ocorreu, resultado esperado e passos para reproduzir")
        String descricao,

        @Schema(description = "Se omitida, assume a prioridade padrão do tópico")
        PrioridadeChamado prioridade
) {}
