package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.execucao.domain.OrigemMaterial;
import com.nexo.app.shared.application.dto.OperacaoMaterial;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecucaoMaterialResponse(
        UUID id,
        MaterialResponse material,
        BigDecimal quantidade,
        OperacaoMaterial operacao,
        String numSerie,
        OrigemMaterial origem
) {}
