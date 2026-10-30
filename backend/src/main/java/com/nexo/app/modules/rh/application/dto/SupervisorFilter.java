package com.nexo.app.modules.rh.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SupervisorFilter(
    UUID idColaborador,
    String nomeColaborador,
    UUID idCoordenador,
    String nomeCoordenador,
    LocalDate dataInicio,
    LocalDate dataFim,
    Boolean ativo,
    String buscaGlobal
) {}
