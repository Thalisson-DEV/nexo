package com.nexo.app.modules.rh.application.dto;

import java.util.UUID;

public record CoordenadorMinResponse(
    UUID id,
    UUID idColaborador,
    String nomeColaborador
) {}
