package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Deprecated(forRemoval = true)
public record SubprojetoVinculoRequest(
        @NotNull(message = "O ID da obra filha é obrigatório")
        UUID idObraFilha,

        TipoVinculoSubprojeto tipoVinculo
) {
}
