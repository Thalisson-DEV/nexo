package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ConfirmarImportacaoMaterialRequest(
    List<MaterialImportacaoDTO> materiais
) {
}
