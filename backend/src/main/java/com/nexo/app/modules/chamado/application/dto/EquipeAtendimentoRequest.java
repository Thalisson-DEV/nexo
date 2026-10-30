package com.nexo.app.modules.chamado.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record EquipeAtendimentoRequest(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 500) String descricao,
        Set<UUID> membros
) {}
