package com.nexo.app.modules.rh.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CoordenadorFilter(
    UUID idColaborador,
    String nomeColaborador,
    LocalDate dataInicio,
    LocalDate dataFim,
    Boolean ativo,
    String buscaGlobal
) {}
