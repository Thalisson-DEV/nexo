package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record para criaÃ§Ã£o ou atualizaÃ§Ã£o de um Processo de NegÃ³cio.
 */
public record ProcessoNegocioRequest(
    @NotBlank(message = "O nome do processo é obrigatório")
    @Size(max = 50, message = "O nome do processo não pode exceder 50 caracteres")
    String nome
) {}
