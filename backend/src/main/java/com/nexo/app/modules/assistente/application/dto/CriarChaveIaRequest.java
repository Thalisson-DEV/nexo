package com.nexo.app.modules.assistente.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido de chave de acesso para clientes que aceitam cabecalho (Claude Code, Cursor...). */
public record CriarChaveIaRequest(
        @NotBlank(message = "Dê um nome para a chave.") @Size(max = 120, message = "Use até 120 caracteres.") String nome,
        boolean permiteEscrita,
        @Min(value = 1, message = "A chave vale no mínimo 1 dia.") @Max(value = 365, message = "A chave vale no máximo 1 ano.") int validadeDias) {
}
