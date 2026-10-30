package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Opção de escolha publicada no schema, já com o rótulo pronto — a tela não
 * traduz constante nenhuma.
 */
@Schema(description = "Opção selecionável de um campo de escolha")
public record OpcaoSchema(

        @Schema(description = "Valor trafegado no payload", example = "URGENTE")
        String valor,

        @Schema(description = "Texto exibido ao usuário", example = "Urgente")
        String rotulo
) {}
