package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotNull;

/** Versão conhecida exigida para avançar a separação técnica. */
public record AlterarSeparacaoTecnicaRequest(@NotNull Long versaoEsperada) {
}
