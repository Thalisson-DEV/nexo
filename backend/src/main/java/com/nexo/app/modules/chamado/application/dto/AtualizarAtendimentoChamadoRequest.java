package com.nexo.app.modules.chamado.application.dto;

import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AtualizarAtendimentoChamadoRequest(
        StatusChamado status,

        @Schema(description = "Nova equipe para encaminhamento")
        UUID equipeId,

        @Schema(description = "Nova prioridade definida na triagem")
        PrioridadeChamado prioridade,

        @Schema(description = "Responsável pelo atendimento; ao iniciar sem informar, assume o operador atual")
        UUID responsavelId,

        @Size(max = 5000, message = "A solução deve ter no máximo 5.000 caracteres.")
        @Schema(description = "Obrigatória quando o status for RESOLVIDO")
        String solucao
) {}
