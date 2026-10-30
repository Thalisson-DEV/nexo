package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusDesvio;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TratarDesviosEmMassaRequest(
        @NotEmpty(message = "Selecione ao menos um desvio")
        @Size(max = 100, message = "É possível tratar no máximo 100 desvios por operação")
        List<UUID> idsDesvios,

        @NotNull(message = "O status do desvio é obrigatório")
        StatusDesvio status,

        String acaoCorretiva,
        LocalDate prazo
) {
    public TratarDesviosEmMassaRequest {
        idsDesvios = idsDesvios == null ? List.of() : List.copyOf(idsDesvios);
    }
}
