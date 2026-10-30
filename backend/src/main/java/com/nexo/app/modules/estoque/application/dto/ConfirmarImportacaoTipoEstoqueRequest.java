package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ConfirmarImportacaoTipoEstoqueRequest(
    List<TipoEstoqueImportacaoDTO> tipos
) {
}
