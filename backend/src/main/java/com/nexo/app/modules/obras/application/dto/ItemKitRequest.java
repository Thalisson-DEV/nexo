package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item do kit. {@code idReferencia} é a Ação (serviço) ou o Material, conforme o tipo.
 */
public record ItemKitRequest(
        UUID id,

        @NotNull(message = "O tipo do item é obrigatório")
        TipoItemKit tipo,

        @NotNull(message = "Informe a ação ou o material do item")
        UUID idReferencia,

        OperacaoItemKit operacao,

        @Size(max = 20, message = "A unidade deve ter até 20 caracteres")
        String unidade,

        @NotNull(message = "A quantidade do item é obrigatória")
        @DecimalMin(value = "0.0001", message = "A quantidade do item deve ser maior que zero")
        BigDecimal quantidade
) {
}
