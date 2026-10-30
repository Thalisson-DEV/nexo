package com.nexo.app.modules.rh.application.dto;

import java.util.UUID;

public record SupervisorMinResponse(
    UUID id,
    UUID idColaborador,
    String nomeColaborador,
    UUID idCoordenador,
    String nomeCoordenador
) {}
