package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request para aprovação de solicitação, permitindo edição de quantidades.
 */
@Schema(description = "Dados para aprovação de solicitação (permite editar, adicionar ou remover itens)")
public record AprovarSolicitacaoRequest(
    @Schema(description = "Lista de itens existentes com quantidades ajustadas", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    List<ItemEdicaoAprovacaoRequest> itensEditados,

    @Schema(description = "Novos materiais adicionados à solicitação no momento da aprovação", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    List<NovoItemAprovacaoRequest> itensAdicionados,

    @Schema(description = "IDs dos itens que devem ser removidos da solicitação", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    List<UUID> itensRemovidos,

    @Schema(description = "Novo almoxarifado de origem; ausente mantém o atual", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    UUID idEstoqueOrigem,

    @Schema(description = "Nova data prevista de saque; ausente mantém a atual", example = "2026-10-25", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    LocalDate dataPrevista
) {
    public AprovarSolicitacaoRequest(
            List<ItemEdicaoAprovacaoRequest> itensEditados,
            List<NovoItemAprovacaoRequest> itensAdicionados,
            List<UUID> itensRemovidos
    ) {
        this(itensEditados, itensAdicionados, itensRemovidos, null, null);
    }

    public record ItemEdicaoAprovacaoRequest(
        UUID idItemSolicitacao,
        BigDecimal novaQuantidade
    ) {}

    public record NovoItemAprovacaoRequest(
        UUID idMaterial,
        BigDecimal quantidade
    ) {}
}
