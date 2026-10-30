package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Distribuição e comparativo de volume de desvios por gravidade")
public record VolumePorGravidade(
        @Schema(description = "Gravidade do desvio")
        GravidadeDesvio gravidade,
        @Schema(description = "Quantidade de desvios da gravidade apontados na coorte")
        long total,
        @Schema(description = "Participação percentual sobre o total de desvios da coorte")
        BigDecimal percentual,
        @Schema(description = "Quantidade de desvios da gravidade na janela anterior equivalente")
        long anterior,
        @Schema(description = "Variação percentual entre o período atual e o anterior")
        BigDecimal variacaoPercentual
) {}
