package com.nexo.app.modules.obras.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Linhas conferidas na pré-visualização para processar. {@code idContrato} vale para kits e carteira.
 */
public record ConfirmarImportacaoPlanilhaRequest<T>(
        UUID idContrato,
        String nomeArquivo,
        @NotEmpty(message = "Não há linhas para importar.")
        @Valid List<T> registros
) {
}
