package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Reversão parcial ou total de uma entrega ou devolução técnica já confirmada. */
@Schema(description = "Estorno tecnico referenciado a operacao e itens originais, com motivo obrigatorio")
public record EstornarOperacaoTecnicaRequest(
        @NotNull Long versaoEsperada,
        @NotNull UUID idOperacaoOriginal,
        @NotBlank @Size(max = 500) String motivo,
        @NotEmpty List<@Valid ItemEstornoTecnicoRequest> itens
) {
    public record ItemEstornoTecnicoRequest(
            @NotNull UUID idItemOperacaoOriginal,
            @NotNull @Positive BigDecimal quantidade,
            String numeroSerie
    ) {
    }
}
