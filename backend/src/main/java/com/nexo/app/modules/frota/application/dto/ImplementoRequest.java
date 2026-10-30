package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ImplementoRequest(
        @NotBlank(message = "Informe a identificação do implemento.") @Size(max = 60) String identificacao,
        @NotNull(message = "Informe o tipo de implemento.") UUID idTipoImplemento,
        @Size(max = 80) String fabricante,
        @Size(max = 100) String modelo,
        @Size(max = 80) String numeroSerie,
        Integer anoFabricacao,
        @NotNull(message = "Informe a base operacional.") UUID idBaseOperacional,
        SituacaoOperacional situacao,
        @Size(max = 1000) String observacao
) {
}
