package com.nexo.app.modules.rh;

import java.time.LocalDate;
import java.util.UUID;

public record SupervisorResponse(
    UUID id,
    ColaboradorResponse colaborador,
    CoordenadorResponse coordenador,
    LocalDate dataInicio,
    LocalDate dataFim,
    boolean ativo
) {}
