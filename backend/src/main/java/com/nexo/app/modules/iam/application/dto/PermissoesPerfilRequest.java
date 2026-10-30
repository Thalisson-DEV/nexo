package com.nexo.app.modules.iam.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Conjunto completo de permissões do perfil: o que não estiver na lista é removido")
public record PermissoesPerfilRequest(
    @NotNull(message = "Informe a lista de permissões (pode ser vazia)")
    @Schema(description = "IDs de todas as permissões que o perfil deve ter")
    Set<@NotNull UUID> idsPermissoes
) {}
