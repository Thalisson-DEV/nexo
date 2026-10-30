package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Detalhes de um item dentro de uma operação de movimentação")
public record MovimentacaoItemResponse(
    @Schema(description = "ID único do registro de movimentação")
    UUID id,

    @Schema(description = "Dados completos do material")
    MaterialResponse material,

    @Schema(description = "Condição do material")
    CondicaoMaterial condicao,

    @Schema(description = "Quantidade movimentada")
    BigDecimal quantidade,

    @Schema(description = "Número de série (se aplicável)")
    String numSerie,

    @Schema(description = "ID do snapshot do item na operacao tecnica")
    UUID idItemOperacao,

    @Schema(description = "ID do item da solicitacao que originou o movimento")
    UUID idItemSolicitacao
) {}
