package com.nexo.app.modules.dashboard.application.dto;

import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Filtros da consulta de turnos. Datas são dias civis de São Paulo. */
public record ConsultaTurnosFilter(
        UUID idEquipe,
        @Parameter(description = "Bases operacionais (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idBaseOperacional,
        @Parameter(description = "Processos (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idProcesso,
        StatusTurno status,

        @Parameter(description = "Abertos a partir deste dia (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate aberturaDe,

        @Parameter(description = "Abertos até este dia, inclusive (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate aberturaAte,

        @Parameter(description = "Só turnos que estiveram abertos hoje, inclusive os abertos em dias anteriores")
        Boolean apenasHoje,

        @Parameter(description = "Busca rápida pelo código da equipe")
        String buscaGlobal
) {

    public ConsultaTurnosFilter {
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
    }
}
