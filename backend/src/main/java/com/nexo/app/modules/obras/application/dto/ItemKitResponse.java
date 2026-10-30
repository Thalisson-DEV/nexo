package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.TipoItemKit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item do kit com os dados da referência (Ação ou Material) já resolvidos para exibição.
 * {@code valorUnitario} e {@code valorTotal} só existem para serviço com valor vigente no contrato.
 */
public record ItemKitResponse(
        UUID id,
        TipoItemKit tipo,
        UUID idReferencia,
        String codigoReferencia,
        String descricaoReferencia,
        OperacaoItemKit operacao,
        String unidade,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal
) {
}
