package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Métricas agregadas de uma coluna do kanban de solicitações técnicas")
public record KanbanMetricasColuna(
    @Schema(description = "Total de solicitações nesta etapa (pode exceder os cards retornados)")
    long total,
    @Schema(description = "Solicitações com dataPrevista antes de hoje e ainda ativas")
    long atrasadas,
    @Schema(description = "Solicitações registradas fora do prazo de saque (foraPrazoSaque=true)")
    long foraPrazo,
    @Schema(description = "Dias desde a data de solicitação mais antiga desta etapa; nulo se sem registros")
    Long diasMaisAntiga
) {}
