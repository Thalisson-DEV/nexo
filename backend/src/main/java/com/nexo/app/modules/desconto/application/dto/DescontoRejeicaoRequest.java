package com.nexo.app.modules.desconto.application.dto;

import jakarta.validation.constraints.NotBlank;

public record DescontoRejeicaoRequest(
        @NotBlank(message = "O motivo da rejeição é obrigatório.") String motivoRejeicao
) {}
