package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotNull;

public record ConcluirChecklistGovernancaRequest(@NotNull Long versaoEsperada) {
}
