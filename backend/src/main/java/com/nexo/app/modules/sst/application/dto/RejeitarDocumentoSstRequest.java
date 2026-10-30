package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejeitarDocumentoSstRequest(
        @NotBlank @Size(max = 2000) String motivo
) {
}
