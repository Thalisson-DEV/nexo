package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ConfirmarImportacaoEstoqueRequest(
    List<EstoqueImportacaoDTO> estoques
) {
}
