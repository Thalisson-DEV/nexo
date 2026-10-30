package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.StatusAutomacao;
import jakarta.validation.constraints.NotNull;

/** Ativar ou pausar. */
public record StatusAutomacaoRequest(
        @NotNull StatusAutomacao status
) {
}
