package com.nexo.app.modules.rh.application.dto;

import java.util.UUID;

/**
 * DTO simplificado para carregar listas de seleção (dropdowns) no frontend.
 */
public record ColaboradorMinResponse(
    UUID id,
    String nomeCompleto
) {}
