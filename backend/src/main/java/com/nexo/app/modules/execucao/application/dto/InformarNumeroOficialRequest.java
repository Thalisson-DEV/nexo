package com.nexo.app.modules.execucao.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Número oficial que substitui o número provisório da nota avulsa.
 */
public record InformarNumeroOficialRequest(
        @NotBlank(message = "Informe o número oficial da nota")
        @Size(max = 100, message = "O número oficial tem no máximo 100 caracteres")
        String referenciaNota
) {
}
