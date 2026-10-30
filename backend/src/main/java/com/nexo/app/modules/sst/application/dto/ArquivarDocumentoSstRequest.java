package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArquivarDocumentoSstRequest(
        @NotBlank @Size(max = 2000) String motivo
) {
}
