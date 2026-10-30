package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Record para atualização de dados do colaborador.
 */
public record ColaboradorUpdateRequest(
    @NotBlank(message = "Primeiro nome é obrigatório")
    String primeiroNome,

    @NotBlank(message = "Último nome é obrigatório")
    String ultimoNome,

    @NotBlank(message = "Nome completo é obrigatório")
    String nomeCompleto,

    @NotNull(message = "O ID do processo é obrigatório")
    UUID idProcesso,

    @NotNull(message = "O ID da Base Operacional é obrigatório")
    UUID idBaseOperacional,

    @NotNull(message = "O ID da função é obrigatório")
    UUID idFuncao
) {}
