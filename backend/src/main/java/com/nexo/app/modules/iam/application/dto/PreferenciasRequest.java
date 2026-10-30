package com.nexo.app.modules.iam.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Solicitação para atualizar as preferências de interface do usuário")
public record PreferenciasRequest(
    @NotBlank(message = "O tema é obrigatório")
    @Pattern(regexp = "^(light|dark)$", message = "O tema deve ser 'light' ou 'dark'")
    @Schema(description = "Tema da interface", example = "dark", allowableValues = {"light", "dark"})
    String tema,

    @NotBlank(message = "A cor primária é obrigatória")
    @Schema(description = "Nome da cor primária baseada no PrimeNG", example = "emerald")
    String corPrimaria,

    @NotNull(message = "O status das animações é obrigatório")
    @Schema(description = "Se as animações da interface devem estar ativas", example = "true")
    Boolean animacoesAtivadas
) {}
