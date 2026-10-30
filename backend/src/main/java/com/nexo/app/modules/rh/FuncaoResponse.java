package com.nexo.app.modules.rh;

import java.util.UUID;

public record FuncaoResponse(
    UUID id,
    String nome,
    String descricao,
    boolean ativo
) {}
