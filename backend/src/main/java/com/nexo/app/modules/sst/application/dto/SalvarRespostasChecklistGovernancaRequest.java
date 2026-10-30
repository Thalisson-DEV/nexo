package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SalvarRespostasChecklistGovernancaRequest(
        @NotNull Long versaoEsperada,
        @NotNull List<@Valid RespostaChecklistGovernancaRequest> respostas) {
}
