package com.nexo.app.modules.contrato.application.dto;

import java.util.List;

public record ConfirmarImportacaoContratoRequest(
    List<ContratoImportacaoDTO> contratos
) {
}
