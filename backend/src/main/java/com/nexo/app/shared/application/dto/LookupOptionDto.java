package com.nexo.app.shared.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Opção mínima para componentes de seleção e autocomplete.
 *
 * <p>O contrato deliberadamente não carrega a entidade de origem. {@code value}
 * identifica o registro e {@code label} é o texto pronto para exibição.</p>
 */
@Schema(description = "Opção mínima para seletores e autocompletes")
public record LookupOptionDto(
        @Schema(description = "Identificador estável enviado pelo seletor") String value,
        @Schema(description = "Texto apresentado ao usuário") String label) {

    public LookupOptionDto {
        Objects.requireNonNull(value, "value é obrigatório");
        Objects.requireNonNull(label, "label é obrigatório");
    }
}
