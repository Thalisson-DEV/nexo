package com.nexo.app.modules.rh;

import java.util.UUID;

public record CentroFaturamentoResponse(
    UUID id,
    String nome,
    boolean ativo
) {}
