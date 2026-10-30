package com.nexo.app.modules.automacao.application.dto;

import java.util.UUID;

/** Arquivo gerado numa execução. */
public record ArquivoExecucaoResponse(
        UUID id,
        String nome,
        String tipoConteudo,
        long tamanhoBytes
) {
}
