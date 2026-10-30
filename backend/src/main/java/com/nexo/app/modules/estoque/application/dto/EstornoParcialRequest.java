package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO para solicitação de estorno parcial de itens de uma solicitação de material.
 */
public record EstornoParcialRequest(
    @NotEmpty(message = "A lista de itens para estorno não pode estar vazia")
    @Valid
    List<ItemEstornoRequest> itens
) {
    public record ItemEstornoRequest(
        @NotNull(message = "O ID do item da solicitação é obrigatório")
        UUID idItemSolicitacao,

        @NotNull(message = "A quantidade a estornar é obrigatória")
        @Positive(message = "A quantidade a estornar deve ser positiva")
        BigDecimal quantidade
    ) {}
}
