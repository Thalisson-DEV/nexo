package com.nexo.app.modules.desconto.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DescontoItemResponse(
        UUID id,
        String descricaoMaterial,
        TipoEquipamentoResponse tipoEquipamento,
        BigDecimal valor
) {}
