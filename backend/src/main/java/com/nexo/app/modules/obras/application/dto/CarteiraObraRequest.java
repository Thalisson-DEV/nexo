package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.PrioridadeObra;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados de carteira editáveis na obra. {@code valorProjeto} nulo mantém o valor atual.
 */
public record CarteiraObraRequest(
        UUID idContrato,
        ClasseTensao classeTensao,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate prazoRegulado,
        PrioridadeObra prioridade,
        @Size(max = 40, message = "O lote previsto deve ter até 40 caracteres") String lotePrevisto,
        @DecimalMin(value = "0", message = "O valor de projeto não pode ser negativo") BigDecimal valorProjeto,
        @DecimalMin(value = "0", message = "O valor de material previsto não pode ser negativo") BigDecimal valorMaterialPrevisto,
        @DecimalMin(value = "0", message = "O valor de serviço previsto não pode ser negativo") BigDecimal valorServicoPrevisto
) {
}
