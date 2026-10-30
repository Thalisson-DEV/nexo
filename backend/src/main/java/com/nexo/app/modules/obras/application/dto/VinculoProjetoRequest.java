package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VinculoProjetoRequest(
        @NotNull(message = "O ID do projeto relacionado é obrigatório")
        UUID idProjetoRelacionado,

        @NotNull(message = "O tipo do vínculo entre projetos é obrigatório")
        TipoVinculoProjeto tipoVinculo
) {
}
