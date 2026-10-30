package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotBlank;

public record FuncaoRequest(
    @NotBlank(message = "O nome da função é obrigatório")
    String nome,
    String descricao
) {}
