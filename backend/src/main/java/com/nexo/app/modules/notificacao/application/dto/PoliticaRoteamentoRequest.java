package com.nexo.app.modules.notificacao.application.dto;

import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.OperadorCondicao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PoliticaRoteamentoRequest(
        @NotBlank @Size(max = 80) String chaveGatilho,
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 500) String descricao,
        @NotEmpty @Valid List<RotaRequest> rotas
) {
    public record RotaRequest(
            @Min(0) int ordem,
            boolean padrao,
            @NotNull UUID usuarioId,
            @Valid List<CondicaoRequest> condicoes
    ) {
    }

    public record CondicaoRequest(
            @NotBlank String campo,
            @NotNull OperadorCondicao operador,
            @NotBlank String valor
    ) {
    }
}
