package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmarImportacaoEventoSstRequest(
        @NotEmpty(message = "Informe ao menos um evento para importar")
        List<@Valid EventoSstImportacaoDTO> eventos
) {
}
