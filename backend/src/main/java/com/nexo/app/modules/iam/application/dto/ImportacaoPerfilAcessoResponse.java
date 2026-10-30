package com.nexo.app.modules.iam.application.dto;

import java.util.List;

public record ImportacaoPerfilAcessoResponse(
    List<PerfilAcessoImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
