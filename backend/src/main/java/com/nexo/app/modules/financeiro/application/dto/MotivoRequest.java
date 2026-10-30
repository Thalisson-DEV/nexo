package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Justificativa exigida nas ações que precisam de motivo registrado — rejeição,
 * cancelamento e bloqueio de fornecedor.
 */
public record MotivoRequest(

        @Schema(description = "Motivo da ação, registrado na trilha de auditoria",
                example = "Orçamento acima do praticado; renegociar com o fornecedor.")
        @NotBlank(message = "O motivo é obrigatório.")
        String motivo
) {}
