package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.MotivoEncerramentoCorpoSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EncerramentoCorpoSstRequest(
        @NotNull Long versao,
        @NotNull LocalDate dataEfeito,
        @NotNull MotivoEncerramentoCorpoSst motivo,
        @NotBlank @Size(max = 500) String justificativa,
        @Size(max = 255) String referenciaEvidenciaAdministrativa
) {
}
