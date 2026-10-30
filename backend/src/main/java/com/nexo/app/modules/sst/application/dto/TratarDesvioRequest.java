package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusDesvio;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Atualização do tratamento de um desvio (status, ação corretiva e prazo).
 */
public record TratarDesvioRequest(
        @NotNull(message = "O status do desvio é obrigatório")
        StatusDesvio status,

        String acaoCorretiva,
        LocalDate prazo
) {}
