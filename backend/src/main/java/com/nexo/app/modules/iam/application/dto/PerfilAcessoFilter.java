package com.nexo.app.modules.iam.application.dto;

import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;

/**
 * Filtro dinâmico para listagem de Perfis de Acesso.
 */
public record PerfilAcessoFilter(
    @Parameter(description = "ID do Perfil de Acesso")
    UUID id,

    @Parameter(description = "Nome do Perfil de Acesso (busca parcial)")
    String nome,

    @Parameter(description = "Status do Perfil de Acesso")
    Boolean ativo,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {
}
