package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Fato operacional comercial no mesmo formato de leitura do fluxo tecnico. */
public record OperacaoSolicitacaoComercialResponse(
        UUID id,
        TipoOperacaoSolicitacao tipo,
        UUID idEquipe,
        UUID idColaboradorSaque,
        boolean saqueForaEquipe,
        UUID idUsuarioOperador,
        OffsetDateTime realizadaEm,
        String motivo,
        UUID idOperacaoOriginal,
        List<Item> itens
) {
    public record Item(
            UUID id,
            UUID idItemSolicitacao,
            UUID idMaterial,
            String codigoMaterial,
            String descricaoMaterial,
            BigDecimal quantidade,
            String numeroSerie,
            UUID idItemOperacaoOriginal
    ) {
        public static Item de(MovimentacaoEstoque movimento) {
            return new Item(
                    movimento.getId(), null, movimento.getMaterial().getId(),
                    movimento.getMaterial().getCodigo(), movimento.getMaterial().getDescricao(),
                    movimento.getQuantidade(), movimento.getNumSerie(), null);
        }
    }
}
