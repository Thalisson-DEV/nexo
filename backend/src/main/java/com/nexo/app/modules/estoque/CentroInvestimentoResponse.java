package com.nexo.app.modules.estoque;

import java.util.UUID;

public record CentroInvestimentoResponse(
    UUID id,
    String codigo,
    boolean ativo
) {}
