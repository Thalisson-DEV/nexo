package com.nexo.app.modules.execucao.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Filtros da listagem de tipos de serviço")
public record TipoServicoFilter(

        @Schema(description = "Busca por código ou descrição")
        String buscaGlobal,

        @Schema(description = "Código exato da classificação")
        String codigo,

        @Schema(description = "Ativos (true), inativos (false) ou todos (vazio)")
        Boolean ativo
) {
}
