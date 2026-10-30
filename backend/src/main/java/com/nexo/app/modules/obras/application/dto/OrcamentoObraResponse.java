package com.nexo.app.modules.obras.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orçamento previsto da obra para comparação com o planejamento. Sem importação, {@code importado} é falso e a
 * lista de itens vem vazia.
 */
public record OrcamentoObraResponse(
        UUID idObra,
        boolean importado,
        OffsetDateTime dataImportacao,
        BigDecimal valorMaterialPrevisto,
        BigDecimal valorServicoPrevisto,
        BigDecimal totalServicosImportados,
        List<ItemOrcamentoObraResponse> itens
) {
}
