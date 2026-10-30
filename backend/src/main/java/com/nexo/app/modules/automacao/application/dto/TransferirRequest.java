package com.nexo.app.modules.automacao.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Novo responsável pela automação. */
public record TransferirRequest(
        @NotNull UUID donoId
) {
}
