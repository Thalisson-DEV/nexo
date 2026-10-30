package com.nexo.app.modules.obras.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Serviço previsto no orçamento importado da obra. {@code idAcao} vem preenchido quando o código bate com uma Ação.
 */
public record ItemOrcamentoObraResponse(
        UUID id,
        String codigo,
        String descricao,
        String unidade,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        UUID idAcao
) {
}
