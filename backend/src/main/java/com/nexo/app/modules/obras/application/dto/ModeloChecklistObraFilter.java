package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.ModeloChecklistObra;

public record ModeloChecklistObraFilter(
        String buscaGlobal,
        ModeloChecklistObra.Finalidade finalidade,
        Boolean ativo
) {
}
