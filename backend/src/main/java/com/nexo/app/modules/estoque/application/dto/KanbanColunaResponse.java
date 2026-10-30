package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.MacroEtapaKanban;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Macro-coluna do kanban de solicitações técnicas com sub-etapas agrupadas")
public record KanbanColunaResponse(
        @Schema(description = "Macro-etapa da coluna principal")
        MacroEtapaKanban etapa,

        @Schema(description = "Rótulo legível da macro-etapa")
        String label,

        @Schema(description = "Ícone associado")
        String icone,

        @Schema(description = "Sub-colunas operacionais com seus respectivos cards e totais")
        List<KanbanSubColunaResponse> subColunas,

        @Schema(description = "Métricas agregadas desta macro-coluna")
        KanbanMetricasColuna metricas
) {}
