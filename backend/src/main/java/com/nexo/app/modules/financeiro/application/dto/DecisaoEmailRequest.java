package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.DecisaoPorEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Decisão tomada pela página do link do e-mail")
public record DecisaoEmailRequest(

        @NotNull(message = "Informe a decisão.")
        DecisaoPorEmail.Acao acao,

        @Schema(description = "Valor autorizado na aprovação; vazio assume o solicitado")
        BigDecimal valorAprovado,

        @Schema(description = "Observação da aprovação ou motivo da rejeição (obrigatório na rejeição)")
        String texto
) {}
