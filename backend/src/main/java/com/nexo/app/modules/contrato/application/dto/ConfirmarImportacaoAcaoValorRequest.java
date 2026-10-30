package com.nexo.app.modules.contrato.application.dto;

import java.util.List;

public record ConfirmarImportacaoAcaoValorRequest(
    List<AcaoValorImportacaoDTO> registros
) {
}
