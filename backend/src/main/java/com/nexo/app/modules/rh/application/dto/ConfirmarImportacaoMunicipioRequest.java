package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ConfirmarImportacaoMunicipioRequest(
    List<MunicipioImportacaoDTO> municipios
) {
}
