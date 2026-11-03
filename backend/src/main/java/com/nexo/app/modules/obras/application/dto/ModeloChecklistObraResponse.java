package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.EstruturaChecklist;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;

import java.util.UUID;

public record ModeloChecklistObraResponse(
        UUID id,
        ModeloChecklistObra.Finalidade finalidade,
        String titulo,
        String descricao,
        EstruturaChecklist estrutura,
        int grupos,
        int perguntas,
        boolean emUso,
        boolean ativo
) {
}
