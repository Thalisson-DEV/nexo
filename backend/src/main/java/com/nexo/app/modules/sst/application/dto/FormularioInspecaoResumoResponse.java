package com.nexo.app.modules.sst.application.dto;

import java.util.UUID;

/**
 * Resposta resumida de um formulário de inspeção, para listagens.
 */
public record FormularioInspecaoResumoResponse(
        UUID id,
        String titulo,
        String descricao,
        boolean ativo,
        int totalPerguntas
) {}
