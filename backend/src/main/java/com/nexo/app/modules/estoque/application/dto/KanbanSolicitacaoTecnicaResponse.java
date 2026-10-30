package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resposta do painel kanban de solicitações técnicas")
public record KanbanSolicitacaoTecnicaResponse(
    @Schema(description = "Colunas na ordem operacional: AGUARDANDO_PLANEJAMENTO → PRONTO_PARA_SEPARACAO → EM_SEPARACAO → PENDENTE_ENTREGA → PENDENTE_DEVOLUCAO → CONCLUIDA")
    List<KanbanColunaResponse> colunas
) {}
