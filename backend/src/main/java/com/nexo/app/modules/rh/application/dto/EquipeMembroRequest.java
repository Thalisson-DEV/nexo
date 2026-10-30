package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.rh.domain.EquipeMembro;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EquipeMembroRequest(
    @NotNull(message = "O colaborador é obrigatório")
    UUID idColaborador,

    @NotNull(message = "A função é obrigatória")
    EquipeMembro.FuncaoMembro funcao
) {}
