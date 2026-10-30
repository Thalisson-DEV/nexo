package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmarImportacaoSupervisorRequest(
    @NotEmpty(message = "A lista de supervisores não pode estar vazia")
    List<SupervisorImportacaoDTO> supervisores
) {}
