package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EstoqueRequest(
    @NotBlank(message = "O nome identificador do estoque é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nomeIdentificador,

    @NotNull(message = "O tipo de estoque é obrigatório")
    UUID idTipoEstoque,

    @NotNull(message = "A base operacional de saque é obrigatória")
    UUID idBaseOperacional,

    UUID idEquipe
) {}
