package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compra aprovada vista do lado do pagamento: o essencial para o solicitante
 * confirmar que escolheu a compra certa e para quem autoriza enxergar a origem.
 */
@Schema(description = "Resumo de uma solicitação de compra vinculada")
public record SolicitacaoCompraResumoResponse(

        UUID id,

        @Schema(description = "Número sequencial usado no dia a dia", example = "1042")
        Long numero,

        StatusSolicitacao status,

        @Schema(description = "Valor autorizado na compra")
        BigDecimal valorAprovado,

        @Schema(description = "Fornecedor da compra, já resolvido")
        String fornecedor,

        String descricao
) {}
