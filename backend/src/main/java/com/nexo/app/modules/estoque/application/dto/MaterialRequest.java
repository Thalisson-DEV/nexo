package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record MaterialRequest(
    @NotBlank(message = "O código do material é obrigatório")
    String codigo,

    @NotBlank(message = "A descrição é obrigatória")
    String descricao,

    String unidadeMedida,
    String suprMatrFlag,
    UUID idProcesso,
    UUID idAvaliacaoTecnica,
    UUID idCentroInvestimento,
    boolean detalhaSerie
) {}
