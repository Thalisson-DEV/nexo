package com.nexo.app.modules.contrato.application.dto;

import java.util.List;

public record ConfirmarImportacaoAcaoRequest(
    List<AcaoImportacaoDTO> acoes
) {
}
