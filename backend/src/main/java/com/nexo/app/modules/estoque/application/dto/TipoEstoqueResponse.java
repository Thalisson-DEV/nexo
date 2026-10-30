package com.nexo.app.modules.estoque.application.dto;

import java.util.UUID;

public record TipoEstoqueResponse(
    UUID id,
    String nome,
    boolean ativo
) {}
