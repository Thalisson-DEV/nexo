package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Filtros de consulta para listagem de tipos de inspeção")
public record TipoInspecaoFilter(

        @Schema(description = "Termo para busca global por nome")
        String buscaGlobal,

        @Schema(description = "Filtro por status ativo (true), inativo (false) ou todos (null)")
        Boolean ativo
) {
}
