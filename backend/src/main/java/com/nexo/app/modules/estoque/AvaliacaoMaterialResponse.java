package com.nexo.app.modules.estoque;

import java.util.UUID;

public record AvaliacaoMaterialResponse(
    UUID id,
    String nome,
    boolean ativo
) {}
