package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.SubEtapaKanban;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sub-coluna operacional dentro de uma macro-etapa do kanban")
public record KanbanSubColunaResponse(
        @Schema(description = "Identificador do sub-status")
        SubEtapaKanban subEtapa,

        @Schema(description = "Rótulo amigável do sub-status")
        String label,

        @Schema(description = "Cor padrão sugerida para o sub-status")
        String corPadrao,

        @Schema(description = "Cards associados a este sub-status")
        List<SolicitacaoSummaryResponse> itens,

        @Schema(description = "Total real de solicitações neste sub-status")
        long total
) {}
