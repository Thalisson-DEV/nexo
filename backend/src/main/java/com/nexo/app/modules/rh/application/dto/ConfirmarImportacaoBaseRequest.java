package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ConfirmarImportacaoBaseRequest(
    List<BaseOperacionalImportacaoDTO> bases
) {
}
