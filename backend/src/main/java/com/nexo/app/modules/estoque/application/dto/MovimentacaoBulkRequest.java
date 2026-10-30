package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados para registro de movimentações de estoque em lote (Ajustes ou Transferências)")
public record MovimentacaoBulkRequest(
    @Schema(description = "ID do estoque de origem. Obrigatório para BAIXA e TRANSFERÊNCIA. Opcional para ENTRADA.", 
            example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    UUID idEstoqueOrigem,

    @Schema(description = "ID do estoque de destino. Obrigatório para ENTRADA e TRANSFERÊNCIA. Opcional para BAIXA.", 
            example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    UUID idEstoqueDestino,

    @Schema(description = "Lista de itens a serem movimentados")
    List<ItemMovimentacaoRequest> itens,

    @Schema(description = "Assinatura do estoque entregador (origem). 'FISICA' ou Base64 PNG ('data:image/png;base64,...')", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String assinaturaEntregador,

    @Schema(description = "Assinatura do estoque recebedor (destino). 'FISICA' ou Base64 PNG ('data:image/png;base64,...')", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String assinaturaRecebedor
) {
    public MovimentacaoBulkRequest(UUID idEstoqueOrigem, UUID idEstoqueDestino, List<ItemMovimentacaoRequest> itens) {
        this(idEstoqueOrigem, idEstoqueDestino, itens, null, null);
    }
    @Schema(description = "Item individual da movimentação")
    public record ItemMovimentacaoRequest(
        @Schema(description = "ID do material", example = "b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID idMaterial,

        @Schema(description = "Quantidade a ser movimentada. Para materiais com série, deve ser sempre 1.000", example = "15.000", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal quantidade,

        @Schema(description = "Número de série. **Obrigatório** se o material detalha série. **Proibido** caso contrário.", 
                example = "SN-123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String numSerie,

        @Schema(description = "Condição do material. Padrão: NOVO", example = "SUCATA", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CondicaoMaterial condicao
    ) {}
}
