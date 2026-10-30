package com.nexo.app.modules.rh;

import java.time.LocalDate;
import java.util.UUID;

public record CoordenadorResponse(
    UUID id,
    ColaboradorResponse colaborador,
    LocalDate dataInicio,
    LocalDate dataFim,
    boolean ativo
) {}
