package com.nexo.app.modules.iam.application.dto;

import java.util.List;

public record ImportacaoUsuarioResponse(
        List<UsuarioImportacaoDTO> registros,
        int totalProcessados,
        int totalSucesso,
        int totalFalha
) {
}
