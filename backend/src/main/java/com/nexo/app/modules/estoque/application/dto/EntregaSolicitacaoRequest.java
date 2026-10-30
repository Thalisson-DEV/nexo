package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request para registro de entrega (parcial ou total) de uma solicitação de material.
 */
@Schema(description = "Dados para confirmação física de entrega de materiais")
public record EntregaSolicitacaoRequest(
    @Schema(description = "Assinatura eletrônica do almoxarife", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    String assinaturaAlmox,

    @Schema(description = "Assinatura eletrônica do representante da equipe", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    String assinaturaEquipe,

    @Schema(description = "Lista de itens originais que estão sendo fisicamente entregues", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ItemEntregaRequest> itens,

    @Schema(description = "Novos materiais adicionados diretamente no balcão (não previstos no pedido)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    List<NovoItemEntregaRequest> itensAdicionados,

    @Schema(description = "IDs dos itens que devem ser removidos da solicitação no momento da entrega", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    List<UUID> itensRemovidos,

    @Schema(description = "Colaborador que realizou o saque dos materiais", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idColaboradorSaque,

    @Schema(description = "Indica que o responsável pelo saque não pertence à equipe vinculada ao estoque de destino")
    Boolean saqueForaEquipe,

    @Schema(description = "Almoxarifado de onde o material sai, quando muda no balcão; ausente mantém o atual")
    UUID idEstoqueOrigem
) {
    public EntregaSolicitacaoRequest(
            String assinaturaAlmox,
            String assinaturaEquipe,
            List<ItemEntregaRequest> itens,
            List<NovoItemEntregaRequest> itensAdicionados,
            List<UUID> itensRemovidos
    ) {
        this(assinaturaAlmox, assinaturaEquipe, itens, itensAdicionados, itensRemovidos, null, null, null);
    }

    public EntregaSolicitacaoRequest(
            String assinaturaAlmox,
            String assinaturaEquipe,
            List<ItemEntregaRequest> itens,
            List<NovoItemEntregaRequest> itensAdicionados,
            List<UUID> itensRemovidos,
            UUID idColaboradorSaque,
            Boolean saqueForaEquipe
    ) {
        this(assinaturaAlmox, assinaturaEquipe, itens, itensAdicionados, itensRemovidos,
                idColaboradorSaque, saqueForaEquipe, null);
    }

    public record ItemEntregaRequest(
        UUID idItemSolicitacao,
        BigDecimal quantidade,
        BigDecimal novaQuantidadeSolicitada,
        String numSerie
    ) {}

    public record NovoItemEntregaRequest(
        UUID idMaterial,
        BigDecimal quantidade,
        BigDecimal quantidadeSolicitada,
        String numSerie
    ) {}
}
