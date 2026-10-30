package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record MotivoRejeicaoFilter(
        @Parameter(description = "Filtro parcial por nome do motivo")
        String nome,

        @Parameter(description = "Filtro parcial por descrição")
        String descricao,

        @Parameter(description = "Filtro pelo status do registro")
        Boolean ativo,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
        String buscaGlobal
) {}
