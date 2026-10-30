package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConferenciaDocumentoSstRequest(@NotNull Decisao decisao,
                                              @Size(max = 1000) String motivo) {
    public enum Decisao { APROVAR, REJEITAR }
}
