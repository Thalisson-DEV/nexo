package com.nexo.app.modules.obras.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmarImportacaoObraRequest(
        @NotEmpty(message = "A lista de registros para importação não pode estar vazia")
        List<ObraImportacaoDTO> registros
) {
}
