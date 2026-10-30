package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
public record ConfirmarImportacaoSolicitacaoTecnicaRequest(
        @NotBlank @Size(max = 500) String token,
        @NotEmpty @Size(max = 2000) List<ChaveTipo> combinacoes,
        boolean reconhecerAvisos
) {
    public ConfirmarImportacaoSolicitacaoTecnicaRequest {
        combinacoes = combinacoes == null ? List.of() : List.copyOf(combinacoes);
    }

    public record ChaveTipo(@NotBlank @Size(max = 100) String chave,
                            @NotBlank @Size(max = 3) String tipo) {
    }
}
