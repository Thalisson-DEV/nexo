package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotNull;

public record AtivacaoCorpoSstRequest(@NotNull Long versao) {
}
