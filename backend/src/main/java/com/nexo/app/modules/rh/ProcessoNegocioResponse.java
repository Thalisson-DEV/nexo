package com.nexo.app.modules.rh;

import java.util.UUID;

/**
 * Record simplificado para processos de negócio.
 */
public record ProcessoNegocioResponse(
    UUID id,
    String nome,
    boolean ativo
) {}