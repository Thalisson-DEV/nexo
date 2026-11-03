package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** O que está hoje no estoque da obra; `idEstoque` é nulo enquanto a obra não tem estoque. */
public record SaldoObraResponse(
        UUID idEstoque,
        List<ItemSaldo> itens
) {
    public SaldoObraResponse {
        itens = itens == null ? List.of() : List.copyOf(itens);
    }

    /** Uma linha por material e condição. */
    public record ItemSaldo(
            String codigo,
            String descricao,
            String unidade,
            CondicaoMaterial condicao,
            BigDecimal quantidade,
            List<String> series
    ) {
        public ItemSaldo {
            series = series == null ? List.of() : List.copyOf(series);
        }
    }
}
