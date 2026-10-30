package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusFornecedor;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filtros da listagem de fornecedores.
 */
public record FornecedorFilter(

        @Schema(description = "Busca rápida por nome fantasia, razão social ou documento")
        String buscaGlobal,

        @Schema(description = "Estágio de curadoria do fornecedor")
        StatusFornecedor status,

        @Schema(description = "Traz apenas fornecedores que aceitam novas solicitações")
        Boolean apenasUtilizaveis,

        @Schema(description = "Filtra por registro ativo (soft delete)")
        Boolean ativo
) {}
