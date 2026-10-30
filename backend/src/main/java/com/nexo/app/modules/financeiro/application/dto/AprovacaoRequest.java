package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Decisão favorável do aprovador.
 *
 * <p>Permitir autorizar por menos do que foi pedido reflete a prática real de
 * aprovar parcialmente; autorizar por mais é recusado, porque mudaria a faixa de
 * alçada que habilitou o aprovador.</p>
 */
public record AprovacaoRequest(

        @Schema(description = "Valor autorizado. Omitido, assume o valor solicitado.", example = "1450.00")
        @DecimalMin(value = "0.01", message = "O valor aprovado deve ser maior que zero.")
        BigDecimal valorAprovado,

        @Schema(description = "Observação do aprovador, registrada na solicitação",
                example = "Aprovado o menor orçamento entre os três apresentados.")
        String observacao
) {}
