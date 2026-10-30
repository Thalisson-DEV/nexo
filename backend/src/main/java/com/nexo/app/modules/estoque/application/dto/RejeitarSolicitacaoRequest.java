package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Payload para rejeição de uma solicitação de material")
public record RejeitarSolicitacaoRequest(
        @NotNull(message = "O ID do motivo de rejeição é obrigatório")
        @Schema(description = "ID do motivo de rejeição padronizado", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID idMotivoRejeicao,

        @NotBlank(message = "A justificativa é obrigatória")
        @Schema(description = "Justificativa detalhada do motivo de rejeição", example = "O material solicitado não possui saldo suficiente e não há previsão de reposição em curto prazo.")
        String justificativa
) {}
