package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CentroFaturamentoRequest(
    @NotBlank(message = "O nome é obrigatório")
    String nome
) {}
