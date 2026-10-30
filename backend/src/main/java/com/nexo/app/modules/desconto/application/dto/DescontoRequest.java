package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.StatusDesconto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DescontoRequest(
        @NotNull(message = "O colaborador é obrigatório.") UUID colaboradorId,
        @NotNull(message = "O motivo de desconto é obrigatório.") UUID motivoId,
        String observacoes,
        @NotNull(message = "A quantidade de parcelas é obrigatória.") @Min(value = 1, message = "A quantidade de parcelas deve ser pelo menos 1.") Integer quantidadeParcelas,
        @NotNull(message = "A data da ocorrência é obrigatória.") OffsetDateTime dataOcorrencia
) {}
